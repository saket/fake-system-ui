package me.saket.fakesystemui

import android.annotation.SuppressLint
import android.graphics.Insets
import android.view.View
import android.view.ViewParent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFold
import androidx.core.view.doOnLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import me.saket.fakesystemui.FakeSystemUiBuilderScope.InsetTransformer
import android.view.WindowInsets as ViewWindowInsets

/**
 * Mimics Android's system UI by rendering fake status and navigation bars. The sizes of the system
 * bars are dispatched as insets to [content]. This can be used for ensuring that our layouts behave
 * correctly when they're dependent on window insets.
 */
@Composable
@RequiresApi(30)  // TODO: can this be removed?
fun FakeSystemUiBuilder(
  modifier: Modifier = Modifier,
  content: @Composable FakeSystemUiBuilderScope.() -> Unit,
) {
  val hostView = LocalView.current
  val colorExtractor = remember { LayoutColorExtractor() }
  val keyboardController = remember { RealObservableSoftwareKeyboardController() }

  Box(
    modifier
      .fillMaxSize()
      .then(colorExtractor.recordBitmap())
  ) {
    val boxScope: BoxScope = this
    val builderScope = remember(boxScope) {
      RealFakeSystemUiBuilderScope(
        boxScope = boxScope,
        colorExtractor = colorExtractor,
        keyboardController = keyboardController,
      )
    }

    LaunchedEffect(builderScope) {
      builderScope.insetTransformers.collect { transformers ->
        val insets = transformers.fastFold(initial = ViewWindowInsets.Builder().build()) { result, transformer ->
          transformer.transform(result)
        }

        // Apply the insets to the root so that they're propagated to fake overlays as well.
        // Dispatch immediately to ensure the insets are measured before layout (important
        // for scrolling).
        hostView.rootView.dispatchApplyWindowInsets(insets)
        hostView.rootView.dispatchWindowInsetsAnimationProgress(insets, emptyList())

        // TODO: We shouldn't have to dispatch these insets twice, but different tests
        //  fail if we omit either the immediate dispatch or the `doOnLayout` dispatch
        // Dispatch again because some of the overlay tests don't work correctly without it:
        hostView.rootView.doOnLayout {
          it.dispatchApplyWindowInsets(insets)
          it.dispatchWindowInsetsAnimationProgress(insets, emptyList())
        }
      }
    }

    CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboardController) {
      Box(Modifier.showKeyboardOnFocus()) {
        content(builderScope)
      }
    }
  }
}

sealed interface FakeSystemUiBuilderScope : BoxScope {

  val keyboardController: ObservableSoftwareKeyboardController

  // todo: document windowLightStatusBar.
  @Composable
  fun StatusBar(
    modifier: Modifier = Modifier,
    windowLightStatusBar: Boolean = true,
  )

  /**
   * @param navigationMode Used to decide which system navigation style to render.
   * [FakeSystemUiNavigationMode.ThreeButtonNavigation] is used by default, because it is the trickier
   * style to get right. If your screen looks good with three-button navigation, then it probably
   * looks good with gesture navigation.
   */
  @Composable
  fun NavigationBar(
    modifier: Modifier = Modifier,
    navigationMode: FakeSystemUiNavigationMode = FakeSystemUiNavigationMode.ThreeButtonNavigation(),
  )

  @Composable
  fun SoftKeyboard(
    modifier: Modifier = Modifier,
  )

  // todo: doc
  fun registerInsetTransformer(
    transform: (ViewWindowInsets) -> ViewWindowInsets
  ): InsetTransformer

  // todo: doc
  interface InsetTransformer {
    fun transform(insets: ViewWindowInsets): ViewWindowInsets
    fun unregister()
  }
}

interface ObservableSoftwareKeyboardController : SoftwareKeyboardController {
  val showRequests: MutableStateFlow<Boolean>
}

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

    MatchParentPopup {
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
        val contentColor = if (windowLightStatusBar) Color.Black else Color.White
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
        ViewWindowInsets.Builder(insets)
          .setInsets(ViewWindowInsets.Type.statusBars(), statusBarInsets)
          .setInsetsIgnoringVisibility(ViewWindowInsets.Type.statusBars(), statusBarInsets)
          .build()
      }
      onDispose {
        transformer.unregister()
      }
    }
  }

  @Composable
  override fun NavigationBar(
    modifier: Modifier,
    navigationMode: FakeSystemUiNavigationMode
  ) {
    MatchParentPopup {
      Box(
        modifier = modifier
          .fillMaxSize()
          .wrapContentHeight(align = Alignment.Bottom)
          .height(navigationMode.height)
          .then(
            if (navigationMode.tintBasedOnUnderneathContent) {
              Modifier.tintBasedOnUnderneathContent(colorExtractor)
            } else {
              Modifier
            }
          ),
        contentAlignment = Alignment.Center,
      ) {
        navigationMode.Content()
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
          /* bottom = */ with(density) { navigationMode.height.roundToPx() }
        )
        ViewWindowInsets.Builder(insets)
          .setInsets(ViewWindowInsets.Type.navigationBars(), navigationBarInsets)
          .setInsetsIgnoringVisibility(ViewWindowInsets.Type.navigationBars(), navigationBarInsets)
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
        .padding(WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues())
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
              style = TextStyle.Default.copy(fontSize = 24.sp, color = Color.White),
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
        ViewWindowInsets.Builder(insets)
          .setInsets(ViewWindowInsets.Type.ime(), keyboardInsets)
          .build()
      }
      onDispose {
        transformer.unregister()
      }
    }
  }

  override fun registerInsetTransformer(transform: (ViewWindowInsets) -> ViewWindowInsets): InsetTransformer {
    val transformer = object : InsetTransformer {
      override fun transform(insets: ViewWindowInsets): ViewWindowInsets {
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
