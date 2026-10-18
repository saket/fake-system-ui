package me.saket.fakesystemui.internal

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.WindowManagerGlobal
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.core.graphics.get
import me.saket.fakesystemui.FakeSystemUiBuilderScope
import me.saket.fakesystemui.NavigationBarTinter

@Stable
@RequiresApi(29)
internal class LayoutLibNavigationBarTinter : NavigationBarTinter {
  private val matrix = Matrix()
  private var lastBitmap: Bitmap? by mutableStateOf(null)
  private var contentPositionOnScreen = Offset.Zero

  // Capturing the app's content on every frame is slow. Capture only while a gesture handle needs
  // a tint, so that tests with three-button navigation or a fixed tint do not become slower.
  //
  // FakeSystemUiBuilder() can't choose a tinter that does nothing for these tests, because it creates
  // the tinter before its content runs. Only the content knows whether it will show a gesture handle
  // and whether that handle has a fixed tint. The handle sends a tint request instead. This changes
  // a value that only draw() reads, so capturing starts in the same frame without a recomposition.
  private var tintRequestCount by mutableIntStateOf(0)

  override val contentModifier: Modifier = ContentCaptureElement(this)

  override fun requestTints(): AutoCloseable {
    tintRequestCount++
    return AutoCloseable { tintRequestCount-- }
  }

  override fun tintBehind(boundsOnScreen: Rect): Color {
    val bitmap = lastBitmap ?: return Color.Black

    // Convert the handle's screen bounds to bitmap coordinates before reading pixels.
    // Paparazzi's accessibility output adds a pane outside the captured content. Clip to the bitmap
    // so that requests outside that content do not pass invalid bounds to Bitmap.createBitmap().
    val bounds = boundsOnScreen
      .translate(-contentPositionOnScreen)
      .roundToIntRect()
      .intersect(IntRect(0, 0, bitmap.width, bitmap.height))
    if (bounds.isEmpty) {
      return Color.Black
    }

    // Down-scaling a bitmap to 1px is one of the fastest way of extracting its prominent color.
    val onePixelBitmap = Bitmap.createBitmap(
      /* source = */ bitmap,
      /* x = */ bounds.left,
      /* y = */ bounds.top,
      /* width = */ bounds.width,
      /* height = */ bounds.height,
      /* m = */
      matrix.also {
        it.reset()
        it.postScale(1f / bounds.width, 1f / bounds.height)
      },

      // Average the pixels rather than dropping.
      /* filter = */ true,
    )

    val isLightBackground = Color(onePixelBitmap[0, 0]).luminance() > 0.4f
    return if (isLightBackground) Color.Black else Color.White
  }

  /** Registered through `META-INF/services`. */
  @RequiresApi(29)
  class Factory : NavigationBarTinter.Factory {
    @Composable
    override fun rememberTinter(): NavigationBarTinter {
      return remember { LayoutLibNavigationBarTinter() }
    }
  }

  @SuppressLint("ModifierNodeInspectableProperties")
  private data class ContentCaptureElement(
    val tinter: LayoutLibNavigationBarTinter,
  ) : ModifierNodeElement<ContentCaptureNode>() {

    override fun create() = ContentCaptureNode(tinter)

    override fun update(node: ContentCaptureNode) {
      node.tinter = tinter
    }
  }

  private class ContentCaptureNode(
    var tinter: LayoutLibNavigationBarTinter,
  ) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var recorder: LayoutLibRenderNodeRecorder? = null
    private val viewPositionOnScreen = IntArray(2)

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
      // Not using positionOnScreen() because it returns infinity in the first frame of Paparazzi tests.
      // Compose UI saves the window's position and updates it only when the animation clock has moved
      // away from 0. Paparazzi's clock starts at 0, and the content is usually positioned only once.
      currentValueOf(LocalView).getLocationOnScreen(viewPositionOnScreen)
      tinter.contentPositionOnScreen = coordinates.positionInRoot() +
        Offset(viewPositionOnScreen[0].toFloat(), viewPositionOnScreen[1].toFloat())
    }

    override fun ContentDrawScope.draw() {
      drawContent()

      if (tinter.tintRequestCount > 0) {
        val rootView = currentValueOf(LocalView).rootView
        if (recorder?.size != size.roundToIntSize()) {
          recorder?.close()
          recorder = LayoutLibRenderNodeRecorder(rootView.context, size.roundToIntSize())
        }

        tinter.lastBitmap = recorder!!.record { canvas ->
          draw(
            density = this,
            layoutDirection = this.layoutDirection,
            canvas = canvas,
            size = this.size,
          ) redirectedDraw@{
            this@draw.drawContent()
          }

          // Popups shown by the app are part of the content behind the handle. Skip the popups that show
          // the fake system bars, otherwise the handle would pick its color from itself.
          WindowManagerGlobal.getInstance().windowViews
            .fastFilter { it !== rootView && it.getTag(FakeSystemUiBuilderScope.InsetOverlayTagKey) == null }
            .sortedByDescending { it.z }
            .fastForEach { popup ->
              popup.getLocationOnScreen(viewPositionOnScreen)
              canvas.save()
              canvas.translate(
                viewPositionOnScreen[0] - tinter.contentPositionOnScreen.x,
                viewPositionOnScreen[1] - tinter.contentPositionOnScreen.y,
              )
              popup.draw(canvas.nativeCanvas)
              canvas.restore()
            }
        }
      }
    }

    override fun onDetach() {
      recorder?.close()
      recorder = null
    }
  }
}
