package me.saket.fakesystemui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import org.junit.Assume.assumeFalse
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class FakeSystemUiTest(
  @TestParameter private val systemTheme: SystemTheme
) {

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5.copy(
      nightMode = systemTheme.nightMode
    )
  )

  @Test fun `show keyboard by default`() {
    if (systemTheme != SystemTheme.LightMode) {
      throw AssumptionViolatedException("variant not needed")
    }

    paparazzi.snapshot {
      FakeSystemUi(
        navigationMode = FakeSystemUiNavigationMode.GestureNavigation(),
        showKeyboardByDefault = true,
      ) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Gray)
        ) {
          BasicText(
            modifier = Modifier
              .imePadding()
              .align(Alignment.BottomCenter)
              .padding(24.dp),
            text = "This text should be positioned above the keyboard.",
            style =
              TextStyle(
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight(500),
                lineHeight = 32.sp,
              ),
          )
        }
      }
    }
  }

  @Test fun `show keyboard automatically when a text field is focused`() {
    if (systemTheme != SystemTheme.LightMode) {
      throw AssumptionViolatedException("variant not needed")
    }

    paparazzi.snapshot {
      FakeSystemUi(
        showKeyboardByDefault = false,
      ) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Gray)
        ) {
          val focusRequester = remember { FocusRequester() }
          LaunchedEffect(Unit) { focusRequester.requestFocus() }

          BasicTextField(
            modifier =
              Modifier
                .imePadding()
                .systemBarsPadding()
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(focusRequester)
                .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                .padding(16.dp),
            state = rememberTextFieldState("Text field"),
            textStyle = TextStyle(fontSize = 16.sp, color = Color.White),
          )
        }
      }
    }
  }

  @Test fun `tint system bars by reading chrome colors`() {
    paparazzi.snapshot {
      FakeSystemUi {
        ChromeConfig(useLightIconsInLightMode = false)

        Box(
          Modifier
            .fillMaxSize()
            .background(if (isSystemInDarkTheme()) Color.Black else Color.White)
        )
      }
    }
  }

  @Test fun `system navigation UI tint`(
    navigationMode: FakeSystemUiNavigationMode = FakeSystemUiNavigationMode.ThreeButtonNavigation,
    enforceTransparentSystemNavigationBar: Boolean = false,
  ) {
    if (navigationMode == FakeSystemUiNavigationMode.GestureNavigation) {
      assumeFalse(enforceTransparentSystemNavigationBar)
    }

    paparazzi.snapshot {
      FakeSystemUi(navigationMode = navigationMode) {
        ChromeConfig(enforceTransparentSystemNavigationBar = enforceTransparentSystemNavigationBar)
        Box(
          Modifier
            .fillMaxSize()
            .background(if (isSystemInDarkTheme()) Color.LightGray else Color.DarkGray)
        )
      }
    }
  }

  @Suppress("unused")
  enum class SystemTheme(val nightMode: NightMode) {
    DarkMode(NightMode.NIGHT),
    LightMode(NightMode.NOTNIGHT),
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FakeSystemUi(
  navigationMode: FakeSystemUiNavigationMode = FakeSystemUiNavigationMode.GestureNavigation(),
  content: @Composable () -> Unit,
) {
  FakeSystemUiBuilder {
    StatusBar()
    content()
    if (WindowInsets.isImeVisible) {
      SoftKeyboard()
    }
    NavigationBar(
      navigationMode = navigationMode,
    )
  }
}
