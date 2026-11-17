package me.saket.fakesystemui.internal

import android.graphics.Insets
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFold
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import me.saket.fakesystemui.FakeSoftwareKeyboardController
import me.saket.fakesystemui.FakeSystemUiBuilderScope
import me.saket.fakesystemui.FakeSystemUiBuilderScope.InsetDispatcher
import me.saket.fakesystemui.FakeWindowProperties
import kotlin.math.sqrt
import android.view.WindowInsets as AndroidWindowInsets

@RequiresApi(30)
internal class RealFakeSystemUiBuilderScope(
  private val boxScope: BoxScope,
  override val keyboardController: FakeSoftwareKeyboardController,
  override val windowProperties: FakeWindowProperties,
  private val hostView: View,
  private val insetsControllerObserver: WindowInsetsControllerObserver,
) : FakeSystemUiBuilderScope, BoxScope by boxScope {

  private var insetDispatchers = emptyList<RealInsetDispatcher>()
  private val pendingInsetOverlays = mutableStateListOf<Any>()

  fun Modifier.awaitInsetOverlayLayout(): Modifier {
    return layout { measurable, constraints ->
      if (pendingInsetOverlays.isEmpty()) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
          placeable.place(0, 0)
        }
      } else {
        // Popups need the host's size before they can report their insets. Give the host a
        // size first, then wait for the popup layouts before content measurement. Otherwise,
        // a scroll container first gets the full host height without the bar insets. Compose
        // reduces its maximum scroll offset to fit that larger visible area. A later inset
        // update restores the maximum, but does not restore the lost scroll offset.
        layout(constraints.maxWidth, constraints.maxHeight) {}
      }
    }
  }

  @Composable
  override fun StatusBar(modifier: Modifier) {
    val contentColor = if (windowProperties.lightStatusBar) Color.Black else Color.White
    val isVisible = insetsControllerObserver.requestedVisibleTypes.collectAsState().value and
      AndroidWindowInsets.Type.statusBars() != 0

    val insetDispatcher = rememberInsetDispatcher { insets, bounds ->
      val statusBarInsets = Insets.of(
        /* left = */ 0,
        /* top = */ bounds.height,
        /* right = */ 0,
        /* bottom = */ 0
      )
      AndroidWindowInsets.Builder(insets)
        .setInsets(
          AndroidWindowInsets.Type.statusBars(),
          if (isVisible) statusBarInsets else Insets.NONE,
        )
        .setInsetsIgnoringVisibility(AndroidWindowInsets.Type.statusBars(), statusBarInsets)
        .setVisible(AndroidWindowInsets.Type.statusBars(), isVisible)
        .build()
    }
    LaunchedEffect(isVisible) {
      insetDispatcher.redispatchWithLastBounds()
    }
    InsetOverlay {
      val visibilityFraction = animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = systemBarAnimationSpec(isVisible),
      )
      Row(
        modifier = modifier
          .fillMaxWidth()
          .height(62.dp)
          .padding(horizontal = 16.dp)
          .dispatchInsetsOnLayout(insetDispatcher)
          .graphicsLayer {
            alpha = visibilityFraction.value
            translationY = -size.height * (1f - visibilityFraction.value)
          },
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
  override fun GestureNavigationBar(
    modifier: Modifier,
    tint: Color,
  ) {
    NavigationBar(modifier) {
      Box(
        Modifier
          .align(Alignment.Center)
          .height(24.dp)
          .requiredSize(width = if (isLargeDisplay) 200.dp else 100.dp, height = 4.dp)
          .background(tint, RoundedCornerShape(4.dp))
      )
    }
  }

  @Composable
  override fun ThreeButtonNavigationBar(modifier: Modifier) {
    val buttonColor = if (windowProperties.lightNavigationBar) {
      Color.Black
    } else {
      Color.White
    }
    val scrimColor = if (windowProperties.navigationBarContrastEnforced) {
      if (windowProperties.lightNavigationBar) {
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
      Box(
        Modifier
          .fillMaxWidth()
          .height(48.dp)
          .background(scrimColor)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.Center)
            .requiredWidthIn(max = 600.dp),
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
  }

  @Composable
  private fun NavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
  ) {
    val isVisible = insetsControllerObserver.requestedVisibleTypes.collectAsState().value and
      AndroidWindowInsets.Type.navigationBars() != 0
    val insetDispatcher = rememberInsetDispatcher { insets, bounds ->
      val navigationBarInsets = Insets.of(
        /* left = */ 0,
        /* top = */ 0,
        /* right = */ 0,
        /* bottom = */ bounds.height,
      )
      AndroidWindowInsets.Builder(insets)
        .setInsets(
          AndroidWindowInsets.Type.navigationBars(),
          if (isVisible) navigationBarInsets else Insets.NONE,
        )
        .setInsetsIgnoringVisibility(AndroidWindowInsets.Type.navigationBars(), navigationBarInsets)
        .setVisible(AndroidWindowInsets.Type.navigationBars(), isVisible)
        .build()
    }
    LaunchedEffect(isVisible) {
      insetDispatcher.redispatchWithLastBounds()
    }

    InsetOverlay {
      val visibilityFraction by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = systemBarAnimationSpec(isVisible),
      )
      Box(
        modifier
          .fillMaxSize()
          .wrapContentHeight(align = Alignment.Bottom)
          .dispatchInsetsOnLayout(insetDispatcher)
          .graphicsLayer {
            alpha = visibilityFraction
            translationY = size.height * (1f - visibilityFraction)
          }
      ) {
        content()
      }
    }
  }

  @Composable
  override fun InsetOverlay(
    content: @Composable () -> Unit
  ) {
    val overlay = remember { Any() }
    DisposableEffect(overlay) {
      pendingInsetOverlays += overlay
      onDispose { pendingInsetOverlays -= overlay }
    }
    MatchParentWidthPopup {
      Box(
        Modifier
          .onGloballyPositioned { pendingInsetOverlays -= overlay }
          .clearAndSetSemantics {}
      ) {
        content()
      }
    }
  }

  @Composable
  @OptIn(ExperimentalLayoutApi::class)
  @Suppress("SpellCheckingInspection")
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

    val rows = if (isLargeDisplay) {
      listOf("QWERT YUIOP", "ASDFG GHJKL", "ZXCV VBNM")
    } else {
      listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    }

    Column(
      modifier = modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .shadow(16.dp)
        .dropShadow(RectangleShape, Shadow(16.dp))
        .background(if (isSystemInDarkTheme()) Color(0xFF1D1F20) else Color(0xFFF0F3F8))
        .dispatchInsetsOnLayout(insetDispatcher)
        .padding(top = 8.dp)
        .clearAndSetSemantics {},
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val isDarkTheme = isSystemInDarkTheme()
      val textColor = if (isDarkTheme) Color.White else Color.Black.copy(alpha = 0.9f)
      val spaceBarColor = if (isDarkTheme) Color.White.copy(alpha = 0.3f) else Color.White
      val dividerColor = if (isDarkTheme) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.05f)
      val maxKeyCount = rows.maxOf { row -> row.count { !it.isWhitespace() } }

      for (row in rows) {
        val keyCount = row.count { !it.isWhitespace() }
        val missingKeySlots = (maxKeyCount - keyCount).coerceAtLeast(0)
        val leadingSpacerWeight = missingKeySlots / 2f
        val trailingSpacerWeight = missingKeySlots - leadingSpacerWeight
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 12.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (leadingSpacerWeight > 0f) {
            Spacer(
              Modifier.weight(leadingSpacerWeight)
            )
          }

          for (letter in row) {
            if (isLargeDisplay && letter.isWhitespace()) {
              Spacer(
                Modifier.weight(2f)
              )
            } else {
              BasicText(
                modifier = Modifier.weight(1f),
                text = letter.toString(),
                style = TextStyle.Default.copy(
                  fontSize = 24.sp,
                  color = textColor,
                  textAlign = TextAlign.Center,
                ),
              )
            }
          }

          if (trailingSpacerWeight > 0f) {
            Spacer(
              Modifier.weight(trailingSpacerWeight)
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
          .fillMaxWidth(fraction = 0.5f)
          .height(28.dp)
          .dropShadow(spaceBarShape, Shadow(2.dp, alpha = 0.05f))
          .background(spaceBarColor, spaceBarShape)
      )

      val navBarHeight = with(LocalDensity.current) {
        WindowInsets.navigationBarsIgnoringVisibility.getBottom(this).toDp()
      }
      Spacer(
        Modifier
          .fillMaxWidth()
          .height(navBarHeight.coerceAtLeast(48.dp))
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

        override fun redispatchWithLastBounds() {
          dispatchWithBounds(overlayBounds)
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

    // Newer layoutlib versions configure DecorView to consume system insets. Dispatch to the
    // Compose host so that synthetic insets reach the composition unchanged.
    hostView.requestLayout()
    hostView.dispatchApplyWindowInsets(insets)
    hostView.dispatchWindowInsetsAnimationProgress(insets, emptyList())
  }

  private companion object {
    // Matches AOSP's system bar move animation:
    // https://android.googlesource.com/platform/frameworks/base/+/
    // 540e22284684/core/java/android/view/InsetsController.java#227
    fun systemBarAnimationSpec(isVisible: Boolean): TweenSpec<Float> {
      return tween(
        durationMillis = if (isVisible) {
          275
        } else {
          340
        },
        easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
      )
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

private interface RealInsetDispatcher : InsetDispatcher {
  val overlayBounds: IntRect
  val buildInsets: (AndroidWindowInsets, boundsInRoot: IntRect) -> AndroidWindowInsets
}

private val isLargeDisplay: Boolean
  @Composable get() = LocalWindowInfo.current.containerDpSize.width >= 600.dp
