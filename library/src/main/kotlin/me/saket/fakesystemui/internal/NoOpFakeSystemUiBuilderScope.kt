package me.saket.fakesystemui.internal

import android.view.WindowInsets
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntRect
import me.saket.fakesystemui.FakeSystemUiBuilderScope
import me.saket.fakesystemui.FakeSystemUiBuilderScope.InsetDispatcher
import me.saket.fakesystemui.FakeWindowProperties

internal class NoOpFakeSystemUiBuilderScope(
  boxScope: BoxScope,
) : FakeSystemUiBuilderScope, BoxScope by boxScope {
  override val keyboardController = RealFakeSoftwareKeyboardController()
  override val windowFlags = FakeWindowProperties()

  @Composable override fun StatusBar(modifier: Modifier) = Unit
  @Composable override fun GestureNavigationBar(modifier: Modifier, tint: Color) = Unit
  @Composable override fun ThreeButtonNavigationBar(modifier: Modifier) = Unit
  @Composable override fun SoftKeyboard(modifier: Modifier) = Unit
  @Composable override fun InsetOverlay(content: @Composable () -> Unit) = Unit

  @Composable
  override fun rememberInsetDispatcher(
    buildInsets: (WindowInsets, boundsInRoot: IntRect) -> WindowInsets,
  ): InsetDispatcher = FakeInsetDispatcher

  data object FakeInsetDispatcher : InsetDispatcher {
    override fun dispatchWithBounds(boundsInRoot: IntRect) = Unit
    override fun redispatchWithLastBounds() = Unit
    override fun unregister() = Unit
  }
}
