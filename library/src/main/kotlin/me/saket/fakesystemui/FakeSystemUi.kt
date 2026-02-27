package me.saket.fakesystemui

import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.flow.MutableStateFlow
import me.saket.fakesystemui.FakeNavigationBarAppearance.GestureNavigation
import me.saket.fakesystemui.FakeNavigationBarAppearance.ThreeButtonNavigation
import me.saket.fakesystemui.internal.LayoutColorExtractor
import me.saket.fakesystemui.internal.RealFakeSystemUiBuilderScope
import me.saket.fakesystemui.internal.RealObservableSoftwareKeyboardController
import me.saket.fakesystemui.internal.showKeyboardOnFocus
import android.view.WindowInsets as AndroidWindowInsets

/**
 * Mimics Android's system UI by rendering fake status and navigation bars. The sizes of the system
 * bars are dispatched as insets to [content]. This can be used for ensuring layouts behave
 * correctly when they're dependent on window insets.
 *
 * ```kotlin
 * @Test fun test() {
 *   paparazzi.snapshot {
 *     FakeSystemUi {
 *       Box(
 *        Modifier
 *          .fillMaxSize()
 *          .background(Color.LightGray)
 *       )
 *     }
 *   }
 * }
 * ```
 *
 * @param navigationBarAppearance Used to decide which system navigation style to render.
 *   [FakeNavigationBarAppearance.ThreeButtonNavigation] is used by default because it is the trickier
 *   style to get right. If your screen looks good with three-button navigation, then it probably
 *   looks good with gesture navigation.
 */
@Composable
@RequiresApi(30)  // TODO: can this be removed?
fun FakeSystemUi(
  navigationBarAppearance: FakeNavigationBarAppearance = ThreeButtonNavigation,
  showKeyboardByDefault: Boolean = false,
  windowProperties: FakeWindowProperties = FakeWindowProperties(),
  content: @Composable () -> Unit,
) {
  FakeSystemUiBuilder(windowFlags = windowProperties) {
    StatusBar()
    content()
    if (showKeyboardByDefault || this.keyboardController.showRequests.collectAsState().value) {
      SoftKeyboard()
    }
    when (navigationBarAppearance) {
      GestureNavigation -> GestureNavigationBar()
      ThreeButtonNavigation -> ThreeButtonNavigationBar()
    }
  }
}

/**
 * Window properties/attributes to control the appearance of fake system bars. These
 * names are terrible, but they mirror the real Android window flags of the same name.
 *
 * @param lightStatusBar When true, status bar content (clock, icons) is drawn dark
 *   for use on light backgrounds. Maps to [android.R.attr.windowLightStatusBar].
 * @param lightNavigationBar When true, navigation bar buttons are drawn dark for
 *   use on light backgrounds. Maps to [android.R.attr.windowLightNavigationBar].
 * @param navigationBarContrastEnforced When true, a translucent scrim is drawn behind the
 *   navigation bar to ensure button contrast. Maps to [android.R.attr.enforceNavigationBarContrast].
 */
data class FakeWindowProperties(
  val lightStatusBar: Boolean = true,
  val lightNavigationBar: Boolean = true,
  val navigationBarContrastEnforced: Boolean = false,
)

/**
 * The navigation bar's appearance, rendered by [FakeSystemUi()][FakeSystemUi].
 *
 * This is an enum so that it can be easily used with any parameterized test runner. */
enum class FakeNavigationBarAppearance {
  GestureNavigation,
  ThreeButtonNavigation,
}

// todo: doc and usage example.
@Composable
@RequiresApi(30)  // TODO: can this be removed?
fun FakeSystemUiBuilder(
  modifier: Modifier = Modifier,
  windowFlags: FakeWindowProperties = FakeWindowProperties(),
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
    val builderScope = remember(boxScope, windowFlags, hostView) {
      RealFakeSystemUiBuilderScope(
        boxScope = boxScope,
        colorExtractor = colorExtractor,
        keyboardController = keyboardController,
        windowFlags = windowFlags,
        hostView = hostView,
      )
    }

    CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboardController) {
      Box(Modifier.showKeyboardOnFocus()) {
        content(builderScope)
      }
    }
  }
}

interface FakeSystemUiBuilderScope : BoxScope {
  // todo: doc.
  val keyboardController: ObservableSoftwareKeyboardController

  // todo: doc
  val windowFlags: FakeWindowProperties

  @Composable
  fun StatusBar(
    modifier: Modifier = Modifier,
  )

  @Composable
  fun GestureNavigationBar(
    modifier: Modifier = Modifier,
  )

  @Composable
  fun ThreeButtonNavigationBar(
    modifier: Modifier = Modifier,
  )

  @Composable
  fun SoftKeyboard(
    modifier: Modifier = Modifier,
  )

  // todo:
  //  - doc.
  /**
   * ```kotlin
   * @Composable
   * fun FakeSystemUiBuilderScope.FooInsets() {
   *   val insetDispatcher = rememberInsetDispatcher { insets, boundsInRoot ->
   *     val fooInsets = Insets.of(
   *       /* left = */ 0,
   *       /* top = */ boundsInRoot.height,
   *       /* right = */ 0,
   *       /* bottom = */ 0
   *     )
   *     WindowInsets.Builder(insets)
   *       .setInsets(WindowInsets.Type…, fooInsets)
   *       .build()
   *   }
   *
   *   InsetOverlay {
   *     Box(
   *       Modifier
   *         .fillMaxWidth()
   *         .height(42.dp)
   *         .dispatchInsetsOnLayout(insetDispatcher)
   *         .background(Color.Red)
   *     )
   *   }
   * }
   * ```
   */
  @Composable
  fun InsetOverlay(
    content: @Composable () -> Unit,
  )

  @Composable
  fun rememberInsetDispatcher(
    buildInsets: (AndroidWindowInsets, boundsInRoot: IntRect) -> AndroidWindowInsets
  ): InsetDispatcher

  fun Modifier.dispatchInsetsOnLayout(dispatcher: InsetDispatcher): Modifier {
    return this.onLayoutRectChanged(debounceMillis = 0) {
      dispatcher.dispatchWithBounds(it.boundsInRoot)
    }
  }

  // todo: doc
  interface InsetDispatcher {
    fun dispatchWithBounds(boundsInRoot: IntRect)
    fun unregister()
  }
}

interface ObservableSoftwareKeyboardController : SoftwareKeyboardController {
  val showRequests: MutableStateFlow<Boolean>
}
