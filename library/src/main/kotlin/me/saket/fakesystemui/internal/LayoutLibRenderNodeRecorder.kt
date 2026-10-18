package me.saket.fakesystemui.internal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.media.ImageReader
import android.view.AttachInfo_Accessor
import android.view.LayoutlibRenderer
import android.view.ThreadedRenderer
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import me.saket.fakesystemui.FakeSystemUiBuilderScope
import java.nio.ByteBuffer
import android.graphics.Canvas as AndroidCanvas

/**
 * Captures hardware-accelerated Canvas drawings into a [Bitmap] using LayoutLib's rendering
 * pipeline.
 *
 * This is the only reliable way to record HW effects such as shared element transitions, ripple
 * animations, overscroll effects, etc. in Paparazzi tests where other screenshot APIs (e.g.,
 * [GraphicsLayer], [ThreadedRenderer], [ImageReader]) are unsupported.
 */
@RequiresApi(29)
internal class LayoutLibRenderNodeRecorder(context: Context, val size: IntSize) : AutoCloseable {
  // Layoutlib exposes the writable image before the first draw. Reuse it across captures.
  private val imageReader = ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, /* maxImages = */ 1)
  private val image = imageReader.acquireLatestImage()!!

  private val renderNode = RenderNode("recorder")
  private val renderNodeDrawer = RenderNodeDrawer(context, renderNode)

  private val hwRenderer: LayoutlibRenderer = AttachInfo_Accessor
    .setAttachInfo(renderNodeDrawer, null)
    .also {
      // Let layoutlib configure its renderer, then release its default buffer. Captures use the reader above.
      it.setup(size.width, size.height, renderNodeDrawer)
      it.reset()
    }

  fun record(recorder: (Canvas) -> Unit): Bitmap {
    try {
      renderNode.setPosition(0, 0, size.width, size.height)
      val renderNodeCanvas = Canvas(renderNode.beginRecording(size.width, size.height))
      recorder(renderNodeCanvas)
    } finally {
      renderNode.endRecording()
    }

    // Rebind the same surface to avoid stale frames after Paparazzi resets its clock between tests.
    hwRenderer.threadedRenderer.setSurface(imageReader.surface)
    hwRenderer.draw(listOf(renderNodeDrawer))
    val bitmap = image.planes[0].buffer.toBitmap(size)

    // Paparazzi's screenshot includes every window, including the window that holds renderNodeDrawer.
    // Clear the recording so that this window does not draw a copy of the content on top of the app.
    renderNode.discardDisplayList()
    return bitmap
  }

  override fun close() {
    hwRenderer.destroy()
    hwRenderer.reset()
    image.close()
    imageReader.close()
    val windowManager = renderNodeDrawer.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    windowManager.removeViewImmediate(renderNodeDrawer)
    renderNode.discardDisplayList()
  }

  /** A dummy ViewGroup for layoutlib, whose renderer requires an independent ViewGroup that can be recorded. */
  @SuppressLint("ViewConstructor")
  private class RenderNodeDrawer(context: Context, private val node: RenderNode) :
    FrameLayout(context) {

    init {
      setTag(FakeSystemUiBuilderScope.InsetOverlayTagKey, Unit)
      importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun dispatchDraw(canvas: AndroidCanvas) {
      canvas.drawRenderNode(node)
    }
  }
}

private fun ByteBuffer.toBitmap(size: IntSize): Bitmap {
  val bitmap = createBitmap(size.width, size.height)
  rewind()
  bitmap.copyPixelsFromBuffer(this)
  return bitmap
}
