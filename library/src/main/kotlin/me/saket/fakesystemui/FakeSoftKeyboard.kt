package me.saket.fakesystemui

import android.annotation.SuppressLint
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.establishTextInputSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// todo: still needed?
internal class ObservableSoftwareKeyboardController : SoftwareKeyboardController {
  val requests = MutableStateFlow(false)

  override fun hide() {
    requests.tryEmit(false)
  }

  override fun show() {
    requests.tryEmit(true)
  }
}

internal fun Modifier.showKeyboardOnFocus(): Modifier {
  return this.then(ShowKeyboardOnFocusModifierElement)
}

@SuppressLint("ModifierNodeInspectableProperties")
private data object ShowKeyboardOnFocusModifierElement :
  ModifierNodeElement<ShowKeyboardOnFocusModifierNode>() {
  override fun create() = ShowKeyboardOnFocusModifierNode()

  override fun update(node: ShowKeyboardOnFocusModifierNode) = Unit
}

private class ShowKeyboardOnFocusModifierNode :
  Modifier.Node(),
  FocusEventModifierNode,
  PlatformTextInputModifierNode,
  CompositionLocalConsumerModifierNode {

  override fun onFocusEvent(focusState: FocusState) {
    if (focusState.isFocused) {
      // A composable was focused. Try to establish an input
      // session to check if the composable is a text field.
      coroutineScope.launch(Dispatchers.Main.immediate) {
        establishTextInputSession {
          currentValueOf(LocalSoftwareKeyboardController)!!.show()
          startInputMethod { TODO("never called in tests") }
        }
      }
    }
  }
}
