package me.saket.fakesystemui.internal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RenderNode
import android.media.Image
import android.view.WindowManagerGlobal
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import me.saket.fakesystemui.FakeSystemUiBuilderScope
import me.saket.fakesystemui.NavigationBarTinter

@Stable
@RequiresApi(29)
internal class LayoutLibNavigationBarTinter(private val context: Context) : NavigationBarTinter {
  private var contentPositionOnScreen = Offset.Zero
  private var contentRecorder: LayoutLibRenderNodeRecorder? = null

  // Turning drawing instructions into pixels is the slowest part of a capture, and its cost grows with the
  // number of pixels. Keep only the drawing instructions of the app's content here. tintBehind() turns only
  // the small area behind a gesture handle into pixels.
  //
  // The same RenderNode is recorded again on every frame. neverEqualPolicy() makes the handles draw again
  // with a tint from the new content.
  private var lastContent: RenderNode? by mutableStateOf(null, neverEqualPolicy())

  // Capturing the app's content on every frame costs time. Capture only while a gesture handle needs
  // a tint, so that tests with three-button navigation or a fixed tint do not become slower.
  //
  // FakeSystemUiBuilder() can't choose a tinter that does nothing for these tests, because it creates
  // the tinter before its content runs. Only the content knows whether it will show a gesture handle
  // and whether that handle has a fixed tint. The handle sends a tint request instead. This changes
  // a value that only draw() reads, so capturing starts in the same frame without a recomposition.
  private var tintRequestCount by mutableIntStateOf(0)

  @Composable
  @SuppressLint("ModifierFactoryExtensionFunction")
  override fun contentModifier(): Modifier {
    val view = LocalView.current
    val viewPositionOnScreen = remember { IntArray(2) }
    val content = remember { RenderNode("content") }
    DisposableEffect(content) {
      onDispose {
        lastContent = null
        content.discardDisplayList()
        contentRecorder?.close()
        contentRecorder = null
      }
    }

    return Modifier
      .onGloballyPositioned { coordinates ->
        // Not using positionOnScreen() because it returns infinity in the first frame of Paparazzi tests.
        // Compose UI saves the window's position and updates it only when the animation clock has moved
        // away from 0. Paparazzi's clock starts at 0, and the content is usually positioned only once.
        view.getLocationOnScreen(viewPositionOnScreen)
        contentPositionOnScreen = coordinates.positionInRoot() +
          Offset(viewPositionOnScreen[0].toFloat(), viewPositionOnScreen[1].toFloat())
      }
      .drawWithContent {
        drawContent()

        if (tintRequestCount > 0) {
          val contentSize = size.roundToIntSize()
          content.setPosition(0, 0, contentSize.width, contentSize.height)
          val canvas = Canvas(content.beginRecording())
          try {
            draw(
              density = this,
              layoutDirection = this.layoutDirection,
              canvas = canvas,
              size = this.size,
            ) redirectedDraw@{
              this@drawWithContent.drawContent()
            }

            // Popups shown by the app are part of the content behind the handle. Skip the popups that show
            // the fake system bars, otherwise the handle would pick its color from itself.
            WindowManagerGlobal.getInstance().windowViews
              .fastFilter { it !== view.rootView && it.getTag(FakeSystemUiBuilderScope.InsetOverlayTagKey) == null }
              .sortedByDescending { it.z }
              .fastForEach { popup ->
                popup.getLocationOnScreen(viewPositionOnScreen)
                canvas.save()
                canvas.translate(
                  viewPositionOnScreen[0] - contentPositionOnScreen.x,
                  viewPositionOnScreen[1] - contentPositionOnScreen.y,
                )
                popup.draw(canvas.nativeCanvas)
                canvas.restore()
              }
          } finally {
            content.endRecording()
          }
          lastContent = content
        }
      }
  }

  override fun requestTints(): AutoCloseable {
    tintRequestCount++
    return AutoCloseable { tintRequestCount-- }
  }

  override fun tintBehind(boundsOnScreen: Rect): Color {
    val content = lastContent ?: return Color.Black

    // Convert the handle's screen bounds to content coordinates before drawing the area behind it.
    // Paparazzi's accessibility output adds a pane outside the captured content. Clip to the content
    // so that requests outside that content do not create an empty capture.
    val bounds = boundsOnScreen
      .translate(-contentPositionOnScreen)
      .roundToIntRect()
      .intersect(IntRect(0, 0, content.width, content.height))
    if (bounds.isEmpty) {
      return Color.Black
    }

    if (contentRecorder?.size != bounds.size) {
      contentRecorder?.close()
      contentRecorder = LayoutLibRenderNodeRecorder(context, bounds.size)
    }
    val pixels = contentRecorder!!.record { canvas ->
      canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
      canvas.nativeCanvas.drawRenderNode(content)
    }
    val isLightBackground = pixels.averageColor(bounds.size).luminance() > 0.4f
    return if (isLightBackground) Color.Black else Color.White
  }

  /** Registered through `META-INF/services`. */
  @RequiresApi(29)
  class Factory : NavigationBarTinter.Factory {
    @Composable
    override fun rememberTinter(): NavigationBarTinter {
      val context = LocalContext.current
      return remember { LayoutLibNavigationBarTinter(context) }
    }
  }
}

/**
 * Returns the average color of all pixels in this plane. The plane must hold RGBA pixels, and [size]
 * must be its width and height in pixels. Alpha is ignored.
 */
private fun Image.Plane.averageColor(size: IntSize): Color {
  // Read the buffer and its strides once. Reading them for each pixel made this loop two times slower.
  val buffer = this.buffer
  val rowStride = this.rowStride
  val pixelStride = this.pixelStride

  var red = 0
  var green = 0
  var blue = 0
  for (y in 0 until size.height) {
    for (x in 0 until size.width) {
      val index = y * rowStride + x * pixelStride
      red += buffer.get(index).toInt() and 0xFF
      green += buffer.get(index + 1).toInt() and 0xFF
      blue += buffer.get(index + 2).toInt() and 0xFF
    }
  }
  val pixelCount = size.width * size.height
  return Color(red / pixelCount, green / pixelCount, blue / pixelCount)
}
