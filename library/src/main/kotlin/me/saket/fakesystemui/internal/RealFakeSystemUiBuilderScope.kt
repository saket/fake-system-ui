package me.saket.fakesystemui.internal

import android.annotation.SuppressLint
import android.graphics.Insets
import android.view.View
import android.view.ViewParent
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import me.saket.fakesystemui.FakeSystemUiBuilderScope
import me.saket.fakesystemui.FakeSystemUiBuilderScope.InsetTransformer
import me.saket.fakesystemui.ObservableSoftwareKeyboardController
import kotlin.math.sqrt

@RequiresApi(30)
internal class RealFakeSystemUiBuilderScope(
  private val boxScope: BoxScope,
  private val colorExtractor: LayoutColorExtractor,
  override val keyboardController: ObservableSoftwareKeyboardController,
) : FakeSystemUiBuilderScope, BoxScope by boxScope {

  val insetTransformers = MutableStateFlow(emptyList<InsetTransformer>())

  @Composable
  override fun StatusBar(
    modifier: Modifier,
    windowLightStatusBar: Boolean,
  ) {
    // todo: could the height be dynamically measured from the layout size?
    val statusBarHeight = 62.dp

    MatchParentWidthPopup {
      Row(
        modifier = modifier
          .fillMaxSize()
          .wrapContentHeight(align = Alignment.Top)
          .height(statusBarHeight)
          .padding(horizontal = 16.dp)
          .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val contentColor =
          if (windowLightStatusBar) Color.Black else Color.White
        BasicText(
          text = "9:00",
          color = { contentColor },
        )
        Spacer(
          Modifier.weight(1f)
        )
        Box(
          Modifier
            .size(16.dp)
            .background(contentColor, CircleShape)
        )
        Box(
          Modifier
            .size(8.dp, 16.dp)
            .background(contentColor)
        )
      }
    }

    val density = LocalDensity.current
    DisposableEffect(density) {
      val transformer = registerInsetTransformer { insets ->
        val statusBarInsets = Insets.of(
          /* left = */ 0,
          /* top = */ with(density) { statusBarHeight.roundToPx() },
          /* right = */ 0,
          /* bottom = */ 0
        )
        WindowInsets.Builder(insets)
          .setInsets(WindowInsets.Type.statusBars(), statusBarInsets)
          .setInsetsIgnoringVisibility(WindowInsets.Type.statusBars(), statusBarInsets)
          .build()
      }
      onDispose {
        transformer.unregister()
      }
    }
  }

  @Composable
  override fun GestureNavigationBar(modifier: Modifier) {
    // todo: does the height need to be hardcoded? can it be measured instead?
    NavigationBar(modifier, height = 24.dp) {
      Box(
        Modifier
          .align(Alignment.Center)
          .tintBasedOnUnderneathContent(colorExtractor)
          .size(width = 100.dp, height = 4.dp)
          .background(Color.Black, RoundedCornerShape(4.dp))
      )
    }
  }

  @Composable
  override fun ThreeButtonNavigationBar(modifier: Modifier, buttonColor: Color, scrimColor: Color) {
    NavigationBar(modifier, height = 48.dp) {
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
            .background(buttonColor, RoundedCornerShape(size = 4.dp))
        )
      }
    }
  }

  @Composable
  private fun NavigationBar(
    modifier: Modifier,
    height: Dp,
    content: @Composable () -> Unit,
  ) {
    MatchParentWidthPopup {
      Box(
        modifier
          .fillMaxSize()
          .wrapContentHeight(align = Alignment.Bottom)
          .requiredHeight(height)
      ) {
        content()
      }

      // Hide the navigation bar from the screenshot used for color extraction. A simpler solution
      // would have involved a `Modifier.drawWithContent()` that skipped the drawing when a screenshot
      // was in progress, but that is weirdly proving impossible to implement with layoutlib.
      val view = LocalView.current
      LaunchedEffect(Unit) {
        view
          .findParentById(android.R.id.content)
          .setTag(LayoutColorExtractor.ExcludedFromColorExtractionTag, Unit)
      }
    }

    val density = LocalDensity.current
    DisposableEffect(density) {
      val transformer = registerInsetTransformer { insets ->
        val navigationBarInsets = Insets.of(
          /* left = */ 0,
          /* top = */ 0,
          /* right = */ 0,
          /* bottom = */ with(density) { height.roundToPx() }
        )
        WindowInsets.Builder(insets)
          .setInsets(WindowInsets.Type.navigationBars(), navigationBarInsets)
          .setInsetsIgnoringVisibility(WindowInsets.Type.navigationBars(), navigationBarInsets)
          .build()
      }
      onDispose {
        transformer.unregister()
      }
    }
  }

  @Composable
  @OptIn(ExperimentalLayoutApi::class)
  override fun SoftKeyboard(modifier: Modifier) {
    val keyboardHeight = 300.dp

    Column(
      modifier = modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(keyboardHeight)
        .background(Color.DarkGray) // TODO: light mode colors.
        .padding(androidx.compose.foundation.layout.WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues())
        .padding(top = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      @Suppress("SpellCheckingInspection")
      listOf("QWERTYUIOP", "ASDFLETSF", "UCKINGGO").forEach { letters ->
        Row(
          modifier = Modifier.height(60.dp),
          horizontalArrangement = Arrangement.spacedBy(24.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          for (letter in letters) {
            BasicText(
              text = letter.toString(),
              style = TextStyle.Default.copy(
                fontSize = 24.sp,
                color = Color.White
              ),
            )
          }
        }
        Box(
          Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.Black.copy(alpha = 0.2f))
        )
      }

      // Space-bar.
      Box(
        Modifier
          .padding(vertical = 16.dp)
          .size(width = 160.dp, height = 28.dp)
          .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
      )
    }

    val density = LocalDensity.current
    DisposableEffect(density) {
      val transformer = registerInsetTransformer { insets ->
        val keyboardInsets = Insets.of(
          /* left = */
          0, /* top = */
          0, /* right = */
          0, /* bottom = */
          with(density) { keyboardHeight.roundToPx() },
        )
        WindowInsets.Builder(insets)
          .setInsets(WindowInsets.Type.ime(), keyboardInsets)
          .build()
      }
      onDispose {
        transformer.unregister()
      }
    }
  }

  override fun registerInsetTransformer(transform: (WindowInsets) -> WindowInsets): InsetTransformer {
    val transformer = object : InsetTransformer {
      override fun transform(insets: WindowInsets): WindowInsets {
        return transform(insets)
      }

      override fun unregister() {
        insetTransformers.update { existing ->
          existing - this
        }
      }
    }
    insetTransformers.update { existing ->
      existing + transformer
    }
    return transformer
  }
}

/** Applies a tint to all canvas drawings made by downstream modifiers. */
@Composable
@RequiresApi(29)
@SuppressLint("ComposeComposableModifier")
private fun Modifier.tintBasedOnUnderneathContent(colorExtractor: LayoutColorExtractor): Modifier {
  var boundsInRoot: IntRect? by remember { mutableStateOf(null) }
  return this
    .onPlaced { coordinates -> boundsInRoot = coordinates.boundsInRoot().roundToIntRect() }
    .tint {
      val colorBehind = colorExtractor.extractColorFromBounds { bitmap ->
        // Note to self: when paparazzi's AccessibilityRenderExtension is
        // used, the window size may be larger than the content size.
        boundsInRoot!!.intersect(IntRect(0, 0, bitmap.width, bitmap.height))
      }
      when {
        colorBehind == null -> Color.Transparent
        colorBehind.luminance() > 0.4f -> Color.Black
        else -> Color.White
      }
    }
}

private fun Modifier.tint(color: () -> Color): Modifier {
  return this.drawWithCache {
    val paint = Paint()
    onDrawWithContent {
      paint.colorFilter = ColorFilter.tint(color(), BlendMode.SrcIn)
      drawIntoCanvas {
        it.saveLayer(size.toRect(), paint)
        drawContent()
        it.restore()
      }
    }
  }
}

private tailrec fun View.findParentById(id: Int, current: ViewParent? = parent): View {
  if (current is View && current.id == id) return current
  val next = current?.parent ?: error("no parent found with id = $id")
  return findParentById(id, next)
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

