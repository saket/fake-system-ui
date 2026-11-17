package me.saket.fakesystemui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import kotlinx.coroutines.test.runTest
import me.saket.fakesystemui.internal.MatchParentWidthPopup
import me.saket.fakesystemui.internal.NoOpSnapshotHandler
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

  @Test fun `column preserves scroll position across snapshots`() = runTest {
    val scrollState = ScrollState(0)
    val hostView = ComposeView(paparazzi.context).apply {
      setContent {
        FakeSystemUi {
          Column(
            Modifier
              .fillMaxSize()
              .systemBarsPadding()
              .verticalScroll(scrollState)
          ) {
            repeat(20) {
              Spacer(Modifier.height(100.dp))
            }
          }
        }
      }
    }

    paparazzi.snapshot(hostView)

    assertThat(scrollState.maxValue).isGreaterThan(0)
    scrollState.scrollTo(scrollState.maxValue)
    val scrollPositionBeforeSnapshot = scrollState.value

    paparazzi.snapshot(hostView)
    assertThat(scrollState.value).isEqualTo(scrollPositionBeforeSnapshot)
  }

  @Test fun `lazy column preserves scroll position across snapshots`() = runTest {
    val scrollState = LazyListState()
    val hostView = ComposeView(paparazzi.context).apply {
      setContent {
        FakeSystemUi {
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .systemBarsPadding(),
            state = scrollState,
          ) {
            items(20) {
              Spacer(Modifier.height(100.dp))
            }
          }
        }
      }
    }

    paparazzi.snapshot(hostView)

    scrollState.scrollToItem(scrollState.layoutInfo.totalItemsCount - 1)
    paparazzi.snapshot(hostView)

    assertThat(scrollState.canScrollForward).isFalse()
    val firstVisibleItemIndexBeforeSnapshot = scrollState.firstVisibleItemIndex
    val firstVisibleItemScrollOffsetBeforeSnapshot = scrollState.firstVisibleItemScrollOffset
    assertThat(firstVisibleItemIndexBeforeSnapshot).isGreaterThan(0)

    paparazzi.snapshot(hostView)
    assertThat(scrollState.firstVisibleItemIndex).isEqualTo(firstVisibleItemIndexBeforeSnapshot)
    assertThat(scrollState.firstVisibleItemScrollOffset).isEqualTo(firstVisibleItemScrollOffsetBeforeSnapshot)
  }
}
