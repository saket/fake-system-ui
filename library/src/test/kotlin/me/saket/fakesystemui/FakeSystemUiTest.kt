package me.saket.fakesystemui

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
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

  @Test fun `system bars`(
    @TestParameter navigationMode: NavigationMode,
  ) {
    paparazzi.snapshot {
      FakeSystemUi(navigationMode) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color(0xFFE6EE9C))
        ) {
          BasicText(
            modifier = Modifier
              .safeDrawingPadding()
              .fillMaxSize()
              .background(Color(0xFF9E9D24))
              .padding(24.dp),
            text = "Safe drawing area",
            style = TextStyle(
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

  @Test fun `show keyboard by default`() {
    paparazzi.snapshot {
      FakeSystemUi(
        showKeyboardByDefault = true,
      ) {
        Box(
          Modifier
            .fillMaxSize()
            .background(if (isSystemInDarkTheme()) Color.Gray else Color.LightGray)
        ) {
          BasicText(
            modifier = Modifier
              .imePadding()
              .align(Alignment.BottomCenter)
              .padding(24.dp),
            text = "This text should be positioned above the keyboard.",
            style = TextStyle(
              color = if (isSystemInDarkTheme()) Color.White else Color.Black,
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
                .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            state = rememberTextFieldState("Text field"),
            textStyle = TextStyle(fontSize = 16.sp, color = Color.White),
            cursorBrush = SolidColor(Color.Blue),
          )
        }
      }
    }
  }

  @Test fun `tinted system bars`() {
    if (systemTheme != SystemTheme.DarkMode) {
      throw AssumptionViolatedException("variant not needed")
    }

    paparazzi.snapshot {
      FakeSystemUiBuilder {
        StatusBar(
          windowLightStatusBar = false,
        )
        NavigationBar(
          navigationMode = FakeSystemUiNavigationMode.ThreeButtonNavigation(
            buttonColor = Color.Yellow,
            scrimColor = Color.Yellow.copy(alpha = 0.2f),
          )
        )

        Box(
          Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
        )
      }
    }
  }

  @Test fun `auto tint navigation bar based on content underneath in real time`() {
    if (systemTheme != SystemTheme.LightMode) {
      throw AssumptionViolatedException("variant not needed")
    }

    paparazzi.gif(end = 1000) {
      val backgroundColor = remember { Animatable(Color.White) }
      LaunchedEffect(Unit) {
        backgroundColor.animateTo(Color.Black, animationSpec = tween(500, easing = LinearEasing))
        backgroundColor.animateTo(Color.White, animationSpec = tween(500, easing = LinearEasing))
      }

      FakeSystemUi(navigationMode = NavigationMode.GestureNavigation) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Gray)
        ) {
          Box(
            Modifier
              .fillMaxWidth()
              .height(60.dp)
              .align(Alignment.BottomCenter)
              .background(backgroundColor.value)
          )
        }
      }
    }
  }
}

@Suppress("unused")
enum class SystemTheme(val nightMode: NightMode) {
  DarkMode(NightMode.NIGHT),
  LightMode(NightMode.NOTNIGHT),
}

enum class NavigationMode {
  GestureNavigation,
  ThreeButtons,
}

// todo: offer a default implementation for FakeSystemUiBuilder.
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FakeSystemUi(
  navigationMode: NavigationMode = NavigationMode.GestureNavigation,
  showKeyboardByDefault: Boolean = false,
  content: @Composable () -> Unit,
) {
  FakeSystemUiBuilder {
    StatusBar()
    content()
    if (showKeyboardByDefault || keyboardController.showRequests.collectAsState().value) {
      SoftKeyboard()
    }
    NavigationBar(
      navigationMode = when (navigationMode) {
        NavigationMode.GestureNavigation -> FakeSystemUiNavigationMode.GestureNavigation()
        NavigationMode.ThreeButtons -> FakeSystemUiNavigationMode.ThreeButtonNavigation()
      },
    )
  }
}

// todo: upstream this to Paparazzi.
private fun Paparazzi.gif(
  start: Long = 0L,
  end: Long = 500L,
  fps: Int = 30,
  composable: @Composable () -> Unit,
) {
  gif(
    view = ComposeView(context).apply {
      setContent(composable)
    },
    start = start,
    end = end,
    fps = fps,
  )
}
