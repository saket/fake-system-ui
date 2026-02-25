package me.saket.fakesystemui.internal

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

// todo: doc?
// Used because
// (a) Popup() doesn't seem to work with paparazzi
// (b) Dialog() has inflexible styling
// (c) Modifier.fillMaxWidth() doesn't work in popups
@Composable
internal fun MatchParentPopup(
  content: @Composable () -> Unit,
) {
  val hostView = LocalView.current
  val compositionContext = rememberCompositionContext()

  DisposableEffect(Unit) {
    val popupLayout = ComposeView(hostView.context).apply {
      id = android.R.id.content
      setViewTreeLifecycleOwner(hostView.findViewTreeLifecycleOwner())
      setViewTreeSavedStateRegistryOwner(hostView.findViewTreeSavedStateRegistryOwner())
      setParentCompositionContext(compositionContext)
      setContent(content)
    }

    val windowManager = hostView.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val layoutParams = WindowManager.LayoutParams().also {
      it.width = if (hostView.width == 0) ViewGroup.LayoutParams.MATCH_PARENT else hostView.width
      it.height = if (hostView.height == 0) ViewGroup.LayoutParams.WRAP_CONTENT else hostView.height
    }
    windowManager.addView(popupLayout, layoutParams)

    // Layoutlib's WindowManager does not seem to assign full widths to layouts even if
    // they use Modifier.fillMaxWidth(). Workaround this by setting a fixed width to overlays.
    hostView.doOnEveryLayout {
      layoutParams.width = it.width
      layoutParams.height = it.height
      windowManager.updateViewLayout(popupLayout, layoutParams)
    }

    onDispose {
      popupLayout.disposeComposition()
      windowManager.removeViewImmediate(popupLayout)
    }
  }
}

/**
 * `doOnLayout` runs only once at the beginning; `doOnEveryLayout` runs every time.
 */
private inline fun View.doOnEveryLayout(crossinline action: (view: View) -> Unit) {
  val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> action(view) }

  if (isAttachedToWindow) {
    addOnLayoutChangeListener(listener)
  }

  addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
    override fun onViewDetachedFromWindow(v: View) {
      removeOnLayoutChangeListener(listener)
    }

    override fun onViewAttachedToWindow(v: View) {
      addOnLayoutChangeListener(listener)
    }
  })
}
