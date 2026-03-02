package me.saket.fakesystemui

import app.cash.paparazzi.DeviceConfig
import com.android.resources.Density
import com.android.resources.Keyboard
import com.android.resources.KeyboardState
import com.android.resources.Navigation
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import com.android.resources.TouchScreen

enum class ScreenshotTestConfig(
  val deviceConfig: DeviceConfig,
) {
  PhoneLight(
    deviceConfig = DeviceConfig.PIXEL_5.copy(
      nightMode = NightMode.NOTNIGHT,
    )
  ),

  PhoneDark(
    deviceConfig = DeviceConfig.PIXEL_5.copy(
      nightMode = NightMode.NIGHT,
    )
  ),

  PhoneLightLarge(
    deviceConfig = DeviceConfig.PIXEL_5.copy(
      nightMode = NightMode.NOTNIGHT,
      fontScale = 2f,
    )
  ),

  Foldable(
    deviceConfig = DeviceConfig.PIXEL_10_PRO_FOLD.copy(
      nightMode = NightMode.NOTNIGHT,
    )
  ),
  ;

  val isDarkTheme: Boolean
    get() = deviceConfig.nightMode == NightMode.NIGHT

  val isLightTheme: Boolean
    get() = !isDarkTheme

  val isLargeFontSize: Boolean
    get() = deviceConfig.fontScale == 2f
}

@Suppress("unused")
enum class SystemTheme(val nightMode: NightMode) {
  DarkMode(NightMode.NIGHT),
  LightMode(NightMode.NOTNIGHT),
}

private val DeviceConfig.Companion.PIXEL_10_PRO_FOLD
  get() = DeviceConfig(
    screenHeight = 2152,
    screenWidth = 2076,
    xdpi = 373,
    ydpi = 373,
    orientation = ScreenOrientation.PORTRAIT,
    density = Density.create(420),
    ratio = ScreenRatio.NOTLONG,
    size = ScreenSize.XLARGE,
    keyboard = Keyboard.NOKEY,
    touchScreen = TouchScreen.FINGER,
    keyboardState = KeyboardState.SOFT,
    softButtons = true,
    navigation = Navigation.NONAV,
    released = "October 14, 2025",
  )
