package me.saket.fakesystemui.internal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManagerImpl
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntSize
import androidx.core.content.getSystemService
import androidx.core.graphics.get
import androidx.core.view.children
import me.saket.fakesystemui.R

@Stable
@RequiresApi(29)
internal class LayoutColorExtractor {
  private val matrix = Matrix()
  private var lastBitmap: Bitmap? by mutableStateOf(null)
  private var overlayDrawTrigger by mutableIntStateOf(0)
  private var isScreenshotInProgress = false

  @Suppress("NAME_SHADOWING")
  fun extractColorFromBounds(bounds: (Bitmap) -> IntRect): Color? {
    val bitmap = lastBitmap
      ?: if (isScreenshotInProgress) return null else error("bitmap hasn't been extracted yet")
    val bounds = bounds(bitmap)

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
      .also {
        if (it.config == Bitmap.Config.HARDWARE) {
          it.setConfig(Bitmap.Config.ARGB_8888)
        }
      }
    return Color(onePixelBitmap[0, 0])
  }

  @Composable
  @SuppressLint("ModifierFactoryExtensionFunction")
  fun recordBitmap(): Modifier {
    val context = LocalContext.current

    return Modifier.drawWithCache {
      val recorder = LayoutLibRenderNodeRecorder(context, size.roundToIntSize())
      onDrawWithContent {
        this@onDrawWithContent.drawContent()

        lastBitmap = recorder.record { canvas ->
          isScreenshotInProgress = true
          draw(
            density = this,
            layoutDirection = this.layoutDirection,
            canvas = canvas,
            size = this.size,
          ) {
            this@onDrawWithContent.drawContent()
          }

          // Include any popups that might be visible as well.
          context.findWindowManagerRootView()?.let { overlays ->
            overlays.children
              .filter { it.getTag(ExcludedFromColorExtractionTag) == null }
              .sortedByDescending { it.z }
              .forEach { it.draw(canvas.nativeCanvas) }
          }
          isScreenshotInProgress = false
        }

        // todo: find a way to get rid of this as it causes an infinite draw loop.
        overlayDrawTrigger++
      }
    }
  }

  companion object {
    val ExcludedFromColorExtractionTag = R.id.excluded_from_color_extraction
  }
}

private fun Context.findWindowManagerRootView(): ViewGroup? {
  val windowManager = getSystemService<WindowManager>()
  return (windowManager as WindowManagerImpl).currentRootView
}
