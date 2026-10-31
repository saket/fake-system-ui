package me.saket.fakesystemui.internal

import android.view.WindowInsets
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

class WindowInsetsControllerObserverTest {
  @get:Rule val paparazzi = Paparazzi(snapshotHandler = NoOpSnapshotHandler())

  @Test fun `keeps observing until every observer is closed`() {
    val firstObserverSystemBarsVisible = mutableListOf<Boolean>()
    val secondObserverSystemBarsVisible = mutableListOf<Boolean>()

    paparazzi.snapshot {
      val view = LocalView.current
      LaunchedEffect(Unit) {
        val controller = view.windowInsetsController!!
        val firstObserver = WindowInsetsControllerObserver(controller)
        val secondObserver = WindowInsetsControllerObserver(controller)

        firstObserverSystemBarsVisible += firstObserver.systemBarsRequestedVisible
        secondObserverSystemBarsVisible += secondObserver.systemBarsRequestedVisible

        controller.hide(WindowInsets.Type.systemBars())
        firstObserverSystemBarsVisible += firstObserver.systemBarsRequestedVisible
        secondObserverSystemBarsVisible += secondObserver.systemBarsRequestedVisible
        firstObserver.close()

        controller.show(WindowInsets.Type.systemBars())
        firstObserverSystemBarsVisible += firstObserver.systemBarsRequestedVisible
        secondObserverSystemBarsVisible += secondObserver.systemBarsRequestedVisible
        secondObserver.close()
      }
    }

    assertThat(firstObserverSystemBarsVisible).containsExactly(true, false, false)
    assertThat(secondObserverSystemBarsVisible).containsExactly(true, false, true)
  }

  @Test fun `can be observed again in a later snapshot`() {
    val systemBarsVisible = mutableListOf<Boolean>()

    paparazzi.snapshot {
      val view = LocalView.current
      LaunchedEffect(Unit) {
        val controller = view.windowInsetsController!!
        val observer = WindowInsetsControllerObserver(controller)
        observer.close()
      }
    }

    paparazzi.snapshot {
      val view = LocalView.current
      LaunchedEffect(Unit) {
        val controller = view.windowInsetsController!!
        val observer = WindowInsetsControllerObserver(controller)
        systemBarsVisible += observer.systemBarsRequestedVisible

        controller.hide(WindowInsets.Type.systemBars())
        systemBarsVisible += observer.systemBarsRequestedVisible

        controller.show(WindowInsets.Type.systemBars())
        systemBarsVisible += observer.systemBarsRequestedVisible

        observer.close()
      }
    }

    assertThat(systemBarsVisible).containsExactly(true, false, true)
  }

  private companion object {
    val WindowInsetsControllerObserver.systemBarsRequestedVisible: Boolean
      get() = requestedVisibleTypes.value and WindowInsets.Type.systemBars() == WindowInsets.Type.systemBars()
  }
}

internal class NoOpSnapshotHandler : SnapshotHandler {
  override fun newFrameHandler(
    snapshot: Snapshot,
    frameCount: Int,
    fps: Int,
  ): SnapshotHandler.FrameHandler {
    // Android unit tests compile against Android's Java API surface, which doesn't include
    // java.awt.image.BufferedImage. A proxy lets this no-op handler avoid naming that type.
    return Proxy.newProxyInstance(
      SnapshotHandler.FrameHandler::class.java.classLoader,
      arrayOf(SnapshotHandler.FrameHandler::class.java),
    ) { _, _, _ -> null }
      as SnapshotHandler.FrameHandler
  }

  override fun close() = Unit
}
