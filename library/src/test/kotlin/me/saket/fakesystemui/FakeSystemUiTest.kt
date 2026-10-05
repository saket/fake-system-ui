package me.saket.fakesystemui

import android.view.WindowInsets
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.cash.paparazzi.Paparazzi
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.delay
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(TestParameterInjector::class)
class FakeSystemUiTest(
  @TestParameter private val config: ScreenshotTestConfig,
) {

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = config.deviceConfig,
  )

  @Test fun `system bars`(
    @TestParameter navigationBarAppearance: FakeNavigationBarAppearance,
  ) {
    if (config.isLargeFontSize) {
      throw AssumptionViolatedException("no text rendered in this test")
    }

    paparazzi.snapshot {
      FakeSystemUi(navigationBarAppearance) {
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

  @Test fun `light system bar content`() {
    if (config.isDarkTheme) {
      throw AssumptionViolatedException("variant not needed")
    }
    if (config.isLargeFontSize) {
      throw AssumptionViolatedException("no text rendered in this test")
    }

    paparazzi.snapshot {
      val windowFlags = FakeWindowProperties(
        lightStatusBar = false,
        lightNavigationBar = false,
        navigationBarContrastEnforced = true,
      )
      FakeSystemUi(windowProperties = windowFlags) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
        )
      }
    }
  }

  @Test fun `dark system bar content with contrast enforced`() {
    if (!config.isDarkTheme) {
      throw AssumptionViolatedException("variant not needed")
    }
    if (config.isLargeFontSize) {
      throw AssumptionViolatedException("no text rendered in this test")
    }

    paparazzi.snapshot {
      val windowFlags = FakeWindowProperties(
        lightStatusBar = true,
        lightNavigationBar = true,
        navigationBarContrastEnforced = true,
      )
      FakeSystemUi(windowProperties = windowFlags) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.LightGray)
        )
      }
    }
  }

  @Test fun keyboard(
    @TestParameter navigationBarAppearance: FakeNavigationBarAppearance,
  ) {
    paparazzi.snapshot {
      FakeSystemUi(
        showKeyboardByDefault = true,
        navigationBarAppearance = navigationBarAppearance,
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
    if (config.isDarkTheme || config.isLargeFontSize) {
      throw AssumptionViolatedException("variant not needed")
    }

    paparazzi.snapshot {
      FakeSystemUi(
        showKeyboardByDefault = false,
        navigationBarAppearance = FakeNavigationBarAppearance.GestureNavigation,
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

  @Test fun `auto tint navigation bar based on content underneath in real time`() {
    if (config.isDarkTheme || config.isLargeFontSize) {
      throw AssumptionViolatedException("variant not needed")
    }

    paparazzi.gif(end = 1000) {
      val backgroundColor = remember { Animatable(Color.White) }
      LaunchedEffect(Unit) {
        backgroundColor.animateTo(Color.Black, animationSpec = tween(500, easing = LinearEasing))
        backgroundColor.animateTo(Color.White, animationSpec = tween(500, easing = LinearEasing))
      }

      FakeSystemUi(navigationBarAppearance = FakeNavigationBarAppearance.GestureNavigation) {
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

  @Test fun `toggle immersive mode`() {
    if (config != ScreenshotTestConfig.PhoneLight) {
      throw AssumptionViolatedException("only one device configuration is needed")
    }

    paparazzi.gif(end = 1_600) {
      FakeSystemUi(
        windowProperties = FakeWindowProperties(
          lightStatusBar = false,
          lightNavigationBar = false,
        ),
      ) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.LightGray)
        )
      }

      val view = LocalView.current
      LaunchedEffect(Unit) {
        delay(100.milliseconds)
        view.windowInsetsController!!.hide(WindowInsets.Type.systemBars())
        delay(350.milliseconds)
        view.windowInsetsController!!.show(WindowInsets.Type.systemBars())
        delay(350.milliseconds)

        @Suppress("DEPRECATION")
        val compatController = ViewCompat.getWindowInsetsController(view)!!
        compatController.hide(WindowInsetsCompat.Type.systemBars())
        delay(350.milliseconds)
        compatController.show(WindowInsetsCompat.Type.systemBars())
      }
    }
  }
}
