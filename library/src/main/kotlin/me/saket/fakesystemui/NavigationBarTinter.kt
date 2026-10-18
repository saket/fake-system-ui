package me.saket.fakesystemui

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import java.util.ServiceLoader
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Picks a color for the gesture navigation handle from the content behind it, so that the handle
 * remains visible over both light and dark backgrounds. [FakeSystemUi] uses this when the gesture
 * navigation tint is [Color.Unspecified].
 *
 * Developers do not create tinters. See `InstalledNavigationBarTinterFactory` for how a tinter is selected.
 */
@Stable
internal interface NavigationBarTinter {
  /**
   * Applied to the app's content, so that the tinter can capture what is drawn behind the handle.
   */
  @SuppressLint("ModifierFactoryExtensionFunction")
  @Composable fun contentModifier(): Modifier

  /**
   * The gesture handle's tint from the latest capture of the area behind it.
   * [boundsOnScreen] describes that area in pixels relative to the screen.
   */
  fun tintBehind(boundsOnScreen: Rect): Color

  /**
   * Called by gesture handles that will read [tintBehind], so that tinters can skip capturing the app's
   * content when no handle needs it. Handles close the returned request when they stop reading tints.
   */
  fun requestTints(): AutoCloseable

  /** Registered through `META-INF/services`. Must have a public constructor with no parameters. */
  @Stable interface Factory {
    @Composable
    fun rememberTinter(): NavigationBarTinter
  }
}

/**
 * Lets an optional module (for example, one for paparazzi) provide its own tinter when developers add it as a
 * dependency, without asking them to write any setup code. Inspired by kotlinx-coroutines, which uses the same
 * approach for installing `Dispatchers.Main`.
 */
internal val InstalledNavigationBarTinterFactory: NavigationBarTinter.Factory by lazy(NONE) {
  ServiceLoader.load(
    /* service = */ NavigationBarTinter.Factory::class.java,
    /* loader = */ NavigationBarTinter.Factory::class.java.classLoader,
  )
    .firstOrNull()
    ?: PlaceholderNavigationBarTinter
}

// todo: replace with a tinter that captures content using rememberGraphicsLayer().
private object PlaceholderNavigationBarTinter : NavigationBarTinter.Factory {
  @Composable
  override fun rememberTinter(): NavigationBarTinter {
    return remember {
      @SuppressLint("ModifierFactoryExtensionFunction")
      object : NavigationBarTinter {
        @Composable override fun contentModifier(): Modifier = Modifier
        override fun tintBehind(boundsOnScreen: Rect): Color = Color.Black
        override fun requestTints(): AutoCloseable = AutoCloseable {}
      }
    }
  }
}
