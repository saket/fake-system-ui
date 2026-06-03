package me.saket.fakesystemui.internal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RenderNode
import android.media.ImageReader
import android.view.AttachInfo_Accessor
import android.view.LayoutlibRenderer
import android.view.ThreadedRenderer
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
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
internal class LayoutLibRenderNodeRecorder(context: Context, private val size: IntSize) {
  private val renderNode = RenderNode("recorder")
  private val renderNodeDrawer = RenderNodeDrawer(context, renderNode)

  private val hwRenderer: LayoutlibRenderer = AttachInfo_Accessor
    .setAttachInfo(renderNodeDrawer, null)
    .also {
      it.setup(size.width, size.height, renderNodeDrawer)
    }

  fun record(recorder: (Canvas) -> Unit): Bitmap {
    try {
      renderNode.setPosition(0, 0, size.width, size.height)
      val renderNodeCanvas = Canvas(renderNode.beginRecording(size.width, size.height))
      recorder(renderNodeCanvas)
    } finally {
      renderNode.endRecording()
    }

    hwRenderer.draw(listOf(renderNodeDrawer))
    return hwRenderer.buffer.toBitmap(size)
  }

  /** A dummy ViewGroup for layoutlib, whose renderer requires an independent ViewGroup that can be recorded. */
  @SuppressLint("ViewConstructor")
  internal class RenderNodeDrawer(context: Context, private val node: RenderNode) :
    FrameLayout(context) {

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
