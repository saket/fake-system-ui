package me.saket.fakesystemui.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class FakeSystemUiBehaviorTest {
  @get:Rule val paparazzi = Paparazzi(snapshotHandler = NoOpSnapshotHandler())

  @Test fun `disposed popup does not receive host layout updates after reattachment`() {
    val hostView = ComposeView(paparazzi.context).apply {
      setContent {
        Box(Modifier.fillMaxSize()) {
          MatchParentWidthPopup {
            Box(Modifier.fillMaxSize())
          }
        }
      }
    }

    // Paparazzi disposes the popup when it detaches this host after each capture.
    // Reattach the same host so a leaked listener tries to update the removed popup.
    repeat(3) {
      paparazzi.snapshot(hostView)
    }
  }
}
