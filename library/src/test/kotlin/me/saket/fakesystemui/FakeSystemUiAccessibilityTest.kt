package me.saket.fakesystemui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class FakeSystemUiAccessibilityTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5,
    // TODO: AccessibilityRenderExtension doesn't support overlays yet. 
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test fun `system bars do not participate in accessibility`(
    @TestParameter navigationBarAppearance: FakeNavigationBarAppearance,
  ) {
    paparazzi.snapshot {
      FakeSystemUi(
        showKeyboardByDefault = true,
        navigationBarAppearance = navigationBarAppearance
      ) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Gray)
            .systemBarsPadding()
        ) {
          BasicText(
            modifier = Modifier.padding(24.dp),
            text = "Only this text should appear in the right pane",
            style = TextStyle(
              color = Color.White,
              fontSize = 28.sp,
              lineHeight = 32.sp,
            ),
          )
        }
      }
    }
  }

  @Test fun `keyboard does not participate in accessibility`() {
    paparazzi.snapshot {
      FakeSystemUi(
        showKeyboardByDefault = true,
        navigationBarAppearance = FakeNavigationBarAppearance.GestureNavigation,
      ) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Gray)
            .systemBarsPadding()
        ) {
          BasicText(
            modifier = Modifier.padding(24.dp),
            text = "Only this text should appear in the right pane",
            style = TextStyle(
              color = Color.White,
              fontSize = 28.sp,
              lineHeight = 32.sp,
            ),
          )
        }
      }
    }
  }
}
