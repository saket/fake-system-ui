package me.saket.fakesystemui.internal

import android.annotation.SuppressLint
import android.graphics.Insets
import android.view.View
import android.view.ViewParent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFold
import androidx.core.view.doOnLayout
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import me.saket.fakesystemui.FakeSoftwareKeyboardController
import me.saket.fakesystemui.FakeSystemUiBuilderScope
import me.saket.fakesystemui.FakeSystemUiBuilderScope.InsetDispatcher
import me.saket.fakesystemui.FakeWindowProperties
import kotlin.math.sqrt
import android.view.WindowInsets as AndroidWindowInsets
import androidx.compose.ui.graphics.shadow.Shadow

@RequiresApi(30)
internal class RealFakeSystemUiBuilderScope(
  private val boxScope: BoxScope,
  private val colorExtractor: LayoutColorExtractor,
  override val keyboardController: FakeSoftwareKeyboardController,
  override val windowFlags: FakeWindowProperties,
  private val hostView: View,
) : FakeSystemUiBuilderScope, BoxScope by boxScope {

  private var insetDispatchers = emptyList<RealInsetDispatcher>()

  @Composable
  override fun StatusBar(modifier: Modifier) {
    val contentColor = if (windowFlags.lightStatusBar) Color.Black else Color.White

    val insetDispatcher = rememberInsetDispatcher { insets, bounds ->
      val statusBarInsets = Insets.of(
        /* left = */ 0,
        /* top = */ bounds.height,
        /* right = */ 0,
        /* bottom = */ 0
      )
      AndroidWindowInsets.Builder(insets)
        .setInsets(AndroidWindowInsets.Type.statusBars(), statusBarInsets)
        .setInsetsIgnoringVisibility(AndroidWindowInsets.Type.statusBars(), statusBarInsets)
        .build()
    }

    InsetOverlay {
      Row(
        modifier = modifier
          .fillMaxWidth()
          .height(62.dp)
          .padding(horizontal = 16.dp)
          .dispatchInsetsOnLayout(insetDispatcher)
          .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
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
  }

  @Composable
  override fun GestureNavigationBar(modifier: Modifier) {
    NavigationBar(modifier) {
      Box(
        Modifier
          .align(Alignment.Center)
          .height(24.dp)
          .tintBasedOnUnderneathContent(colorExtractor)
          .requiredSize(width = 100.dp, height = 4.dp)
          .background(Color.Black, RoundedCornerShape(4.dp))
      )
    }
  }

  @Composable
  override fun ThreeButtonNavigationBar(modifier: Modifier) {
    val buttonColor = if (windowFlags.lightNavigationBar) {
      Color.Black
    } else {
      Color.White
    }
    val scrimColor = if (windowFlags.navigationBarContrastEnforced) {
      if (windowFlags.lightNavigationBar) {
        //https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/com/android/internal/policy/DecorView.java;l=141;drc=61197364367c9e404c7da6900658f1b16c42d0da
        Color.White.copy(alpha = 0.9f)
      } else {
        // android.R.color.system_bar_background_semi_transparent
        Color.Black.copy(alpha = 0.4f)
      }
    } else {
      Color.Transparent
    }

    NavigationBar(modifier) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
  ) {
    val insetDispatcher = rememberInsetDispatcher { insets, bounds ->
      val navigationBarInsets = Insets.of(
        /* left = */ 0,
        /* top = */ 0,
        /* right = */ 0,
        /* bottom = */ bounds.height,
      )
      AndroidWindowInsets.Builder(insets)
        .setInsets(AndroidWindowInsets.Type.navigationBars(), navigationBarInsets)
        .setInsetsIgnoringVisibility(AndroidWindowInsets.Type.navigationBars(), navigationBarInsets)
        .build()
    }

    InsetOverlay {
      Box(
        modifier
          .fillMaxSize()
          .wrapContentHeight(align = Alignment.Bottom)
          .dispatchInsetsOnLayout(insetDispatcher)
      ) {
        content()
      }
    }
  }

  @Composable
  override fun InsetOverlay(
    content: @Composable () -> Unit
  ) {
    MatchParentWidthPopup {
      content()

      // Hide the overlay from the screenshot used for color extraction. A simpler solution would
      // have involved a `Modifier.drawWithContent()` that skipped the drawing when a screenshot
      // was in progress, but that is weirdly proving impossible to implement with layoutlib.
      val view = LocalView.current
      LaunchedEffect(Unit) {
        view
          .findParentById(android.R.id.content)
          .setTag(LayoutColorExtractor.ExcludedFromColorExtractionTag, Unit)
      }
    }
  }

  @Composable
  @OptIn(ExperimentalLayoutApi::class)
  override fun SoftKeyboard(modifier: Modifier) {
    val insetDispatcher = rememberInsetDispatcher { insets, bounds ->
      val keyboardInsets = Insets.of(
        /* left = */ 0,
        /* top = */ 0,
        /* right = */ 0,
        /* bottom = */ bounds.height,
      )
      AndroidWindowInsets.Builder(insets)
        .setInsets(AndroidWindowInsets.Type.ime(), keyboardInsets)
        .build()
    }

    Column(
      modifier = modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(300.dp)
        .shadow(16.dp)
        .dropShadow(RectangleShape, Shadow(16.dp))
        .background(if (isSystemInDarkTheme()) Color(0xFF1D1F20) else Color(0xFFF0F3F8))
        .padding(WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues())
        .dispatchInsetsOnLayout(insetDispatcher)
        .padding(top = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val isDark = isSystemInDarkTheme()
      val textColor = if (isDark) Color.White else Color.Black.copy(alpha = 0.9f)
      val spaceBarColor = if (isDark) Color.White.copy(alpha = 0.3f) else Color.White
      val dividerColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.05f)

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
                color = textColor,
              ),
            )
          }
        }
        Box(
          Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(dividerColor)
        )
      }

      // Space-bar.
      val spaceBarShape = RoundedCornerShape(32.dp)
      Box(
        Modifier
          .padding(vertical = 16.dp)
          .size(width = 160.dp, height = 28.dp)
          .dropShadow(spaceBarShape, Shadow(2.dp, alpha = 0.05f))
          .background(spaceBarColor, spaceBarShape)
      )
    }
  }

  @Composable
  override fun rememberInsetDispatcher(
    buildInsets: (AndroidWindowInsets, boundsInRoot: IntRect) -> AndroidWindowInsets
  ): InsetDispatcher {
    val buildInsets by rememberUpdatedState(buildInsets)
    val dispatcher = remember {
      object : RealInsetDispatcher {
        override var overlayBounds: IntRect = IntRect.Zero
        override val buildInsets get() = buildInsets

        override fun dispatchWithBounds(boundsInRoot: IntRect) {
          this.overlayBounds = boundsInRoot
          dispatchInsets()
        }

        override fun unregister() {
          insetDispatchers -= this
        }
      }
    }
    DisposableEffect(dispatcher) {
      insetDispatchers += dispatcher
      onDispose {
        dispatcher.unregister()
      }
    }
    return dispatcher
  }

  private fun dispatchInsets() {
    val insets = insetDispatchers.fastFold(AndroidWindowInsets.Builder().build()) { result, transformer ->
      transformer.buildInsets(result, transformer.overlayBounds)
    }

    // Apply the insets to the root so that they're propagated to fake overlays as well.
    // Dispatch immediately to ensure the insets are measured before layout (important
    // for scrolling).
    hostView.rootView.requestLayout()
    hostView.rootView.doOnLayout {
      it.dispatchApplyWindowInsets(insets)
      it.dispatchWindowInsetsAnimationProgress(insets, emptyList())
    }
    // TODO: We shouldn't have to dispatch these insets twice, but different tests
    //  fail if we omit either the immediate dispatch or the `doOnLayout` dispatch
    // Dispatch again because some of the overlay tests don't work correctly without it:
    hostView.rootView.post {
      hostView.rootView.dispatchApplyWindowInsets(insets)
      hostView.rootView.dispatchWindowInsetsAnimationProgress(insets, emptyList())
    }
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

private interface RealInsetDispatcher : InsetDispatcher {
  val overlayBounds: IntRect
  val buildInsets: (AndroidWindowInsets, boundsInRoot: IntRect) -> AndroidWindowInsets
}
