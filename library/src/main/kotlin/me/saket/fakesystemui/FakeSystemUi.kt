package me.saket.fakesystemui

import android.os.Build.VERSION.SDK_INT
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.flow.MutableStateFlow
import me.saket.fakesystemui.FakeNavigationBarAppearance.GestureNavigation
import me.saket.fakesystemui.FakeNavigationBarAppearance.ThreeButtonNavigation
import me.saket.fakesystemui.internal.NoOpFakeSystemUiBuilderScope
import me.saket.fakesystemui.internal.RealFakeSoftwareKeyboardController
import me.saket.fakesystemui.internal.RealFakeSystemUiBuilderScope
import me.saket.fakesystemui.internal.WindowInsetsControllerObserver
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
      is GestureNavigation -> GestureNavigationBar(tint = navigationBarAppearance.tint)
      is ThreeButtonNavigation -> ThreeButtonNavigationBar()
    }
  }
}

/**
 * Window properties/attributes to control the appearance of fake system bars. Their
 * names are terrible, but they mirror the real Android window flags with the same names.
 */
data class FakeWindowProperties(
  /**
   * When true, status bar content (clock, icons) is drawn dark
   * for use on light backgrounds. Maps to [android.R.attr.windowLightStatusBar].
   */
  val lightStatusBar: Boolean = true,
  /**
   * When true, three-button navigation buttons are drawn dark for
   * use on light backgrounds. Maps to [android.R.attr.windowLightNavigationBar].
   * Just like Android, this flag does not control the gesture navigation bar's color.
   */
  val lightNavigationBar: Boolean = true,
  /**
   * When true, a translucent scrim is drawn behind the
   * navigation bar to ensure button contrast. Maps to [android.R.attr.enforceNavigationBarContrast].
   */
  val navigationBarContrastEnforced: Boolean = false,
)

/**
 * The navigation bar's appearance, rendered by [FakeSystemUi()][FakeSystemUi].
 */
sealed interface FakeNavigationBarAppearance {
  /**
   * Gesture navigation with a customizable handle tint. [Color.Unspecified] selects automatic tint.
   *
   * For a fixed tint, use `GestureNavigation(tint = Color.White)`. This only changes the fake handle;
   * it does not control the gesture handle on a real device.
   */
  data class GestureNavigation(
    val tint: Color = Color.Unspecified,
  ) : FakeNavigationBarAppearance

  /** Three-button navigation whose appearance follows [FakeWindowProperties]. */
  data object ThreeButtonNavigation : FakeNavigationBarAppearance
}

/**
 * Lower-level alternative to [FakeSystemUi] that gives full control over which system
 * bars and overlays are rendered. Use this when your device has a custom system UI that
 * differs from stock Android: custom-shaped status bars, display cutouts, or proprietary
 * navigation controls.
 */
@Composable
fun FakeSystemUiBuilder(
  modifier: Modifier = Modifier,
  windowFlags: FakeWindowProperties = FakeWindowProperties(),
  content: @Composable FakeSystemUiBuilderScope.() -> Unit,
) {
  if (SDK_INT < 30) {
    Box(modifier.fillMaxSize()) {
      content(NoOpFakeSystemUiBuilderScope(this))
    }
  } else {
    val hostView = LocalView.current
    val navigationBarTinter = InstalledNavigationBarTinterFactory.rememberTinter()
    val keyboardController = remember { RealFakeSoftwareKeyboardController() }
    val insetsControllerObserver = remember { WindowInsetsControllerObserver(hostView.windowInsetsController!!) }

    Box(
      modifier
        .fillMaxSize()
        .then(navigationBarTinter.contentModifier)
    ) {
      val boxScope: BoxScope = this
      val builderScope = remember(boxScope, windowFlags, hostView) {
        RealFakeSystemUiBuilderScope(
          boxScope = boxScope,
          navigationBarTinter = navigationBarTinter,
          keyboardController = keyboardController,
          windowFlags = windowFlags,
          hostView = hostView,
          insetsControllerObserver = insetsControllerObserver,
        )
      }

      CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboardController) {
        Box(Modifier.showKeyboardOnFocus()) {
          content(builderScope)
        }
      }
    }
  }
}

interface FakeSystemUiBuilderScope : BoxScope {
  /** Keyboard controller that can be observed to check whether a soft keyboard show was requested. */
  val keyboardController: FakeSoftwareKeyboardController

  /** Window properties used for controlling the appearance of fake system bars. */
  val windowFlags: FakeWindowProperties

  @Composable
  fun StatusBar(
    modifier: Modifier = Modifier,
  )

  /**
   * Renders a fake gesture navigation bar.
   *
   * @param tint When [Color.Unspecified], selects a contrasting tint based on the content behind the handle.
   *   Automatic tint currently works only in Paparazzi screenshot tests. This tint is intentionally separate
   *   from [FakeWindowProperties.lightNavigationBar] to mimic Android's behavior.
   */
  @Composable
  fun GestureNavigationBar(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
  )

  @Composable
  fun ThreeButtonNavigationBar(
    modifier: Modifier = Modifier,
  )

  @Composable
  fun SoftKeyboard(
    modifier: Modifier = Modifier,
  )

  /**
   * Renders [content] as a full-size overlay on top of the current layout. Works reliably with
   * Paparazzi (unlike Compose's `Popup()`). Overlays are excluded from accessibility and color
   * extraction so they don't interfere with navigation bar tinting.
   *
   * See [rememberInsetDispatcher] for dispatching insets from overlays.
   */
  @Composable
  fun InsetOverlay(
    content: @Composable () -> Unit,
  )

  /**
   * Creates an [InsetDispatcher] that participates in window inset calculation. When
   * the dispatcher's layout bounds change (via [dispatchInsetsOnLayout]), [buildInsets]
   * is called with the current accumulated insets and the overlay's bounds, and should
   * return updated insets that include this overlay's contribution.
   *
   * Typically used together with [InsetOverlay] and [dispatchInsetsOnLayout]:
   *
   * ```kotlin
   * @Composable
   * fun FakeSystemUiBuilderScope.FooBar() {
   *   val insetDispatcher = rememberInsetDispatcher { insets, boundsInRoot ->
   *   val fooInsets = Insets.of(
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
  fun rememberInsetDispatcher(
    buildInsets: (AndroidWindowInsets, boundsInRoot: IntRect) -> AndroidWindowInsets
  ): InsetDispatcher

  fun Modifier.dispatchInsetsOnLayout(dispatcher: InsetDispatcher): Modifier {
    return this.onLayoutRectChanged(debounceMillis = 0) {
      dispatcher.dispatchWithBounds(it.boundsInRoot)
    }
  }

  /** See [rememberInsetDispatcher]. */
  interface InsetDispatcher {
    fun dispatchWithBounds(boundsInRoot: IntRect)
    fun redispatchWithLastBounds()
    fun unregister()
  }

  companion object {
    /**
     * Key for the tag that [FakeSystemUi] applies to popup roots with fake system bars.
     * [NavigationBarTinter] uses this tag to exclude the bars when it samples the app's content.
     */
    val InsetOverlayTagKey: Int = R.id.fake_system_ui_inset_overlay
  }
}

interface FakeSoftwareKeyboardController : SoftwareKeyboardController {
  val showRequests: MutableStateFlow<Boolean>
}
