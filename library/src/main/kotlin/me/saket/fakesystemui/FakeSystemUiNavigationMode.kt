package me.saket.fakesystemui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.sqrt

// todo: does the height need to be hardcoded? can it be measured instead?
// todo: rename to FakeNavigationMode?
// todo: rethink its name now that this is no longer an enum. maybe this doesn't need to exist?
@Stable
abstract class FakeSystemUiNavigationMode(
  val height: Dp,
  val tintBasedOnUnderneathContent: Boolean,
) {

  @Composable
  abstract fun Content()

  class GestureNavigation : FakeSystemUiNavigationMode(
    height = 24.dp,
    tintBasedOnUnderneathContent = true,  // todo: maybe just pass the tint color to Content()?
  ) {
    @Composable
    override fun Content() {
      Box(
        Modifier
          .size(width = 100.dp, height = 4.dp)
          .background(Color.Black, RoundedCornerShape(4.dp))
      )
    }
  }

  class ThreeButtonNavigation(
    val buttonColor: Color = Color.Black,
    val scrimColor: Color = Color.Transparent,
  ) : FakeSystemUiNavigationMode(
    height = 48.dp,
    tintBasedOnUnderneathContent = false,
  ) {
    @Composable
    override fun Content() {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .background(scrimColor),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          Modifier
            .size(16.dp)
            .background(buttonColor, BackButtonShape)
        )
        Box(
          Modifier
            .size(16.dp)
            .background(buttonColor, CircleShape)
        )
        Box(
          Modifier
            .size(16.dp)
            .background(buttonColor, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
        )
      }
    }
  }
}

private object BackButtonShape : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val cornerRadius = size.height / 10f
    val halfCornerRadius = cornerRadius / 2
    val height = size.height + cornerRadius
    val width = sqrt((height * height) - (height / 2) * (height / 2))
    val xOffset = (size.width - width - halfCornerRadius) / 2

    val roundedPolygon = RoundedPolygon(
      vertices = floatArrayOf(
        xOffset + width,
        -halfCornerRadius,
        xOffset + width,
        size.height + halfCornerRadius,
        xOffset,
        size.height / 2,
      ),
      rounding = CornerRounding(radius = cornerRadius),
    )
    return Outline.Generic(roundedPolygon.toPath().asComposePath())
  }
}

