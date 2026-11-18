# fake system ui

Screenshot tests render your UI without any window insets, which makes it easy to miss bugs where content slides under the status bar or gets covered by the keyboard. `FakeSystemUi()` solves this by rendering fake system bars and dispatching real insets to your content.

```groovy
implementation("me.saket.fakesystemui:fakesystemui:1.0.0-alpha01")
```

```kotlin
class FooTest {
  @get:Rule val paparazzi = Paparazzi()
  
  @Test fun test() {
    paparazzi.snapshot {
      FakeSystemUi {
        Screen()
      }
    }   
  }
}
```

| Gesture navigation | Three button navigation |
| --- | --- |
| <img width="400" alt="me saket fakesystemui_FakeSystemUiTest_system_bars PhoneLight,GestureNavigation" src="https://github.com/user-attachments/assets/2835f972-2926-4d58-9817-c96371219d5e" /> | <img width="400" alt="me saket fakesystemui_FakeSystemUiTest_system_bars PhoneLight,ThreeButtonNavigation" src="https://github.com/user-attachments/assets/549ee0ee-476c-4b74-aa19-83b57d0827cb" /> |

#### Soft keyboard

`FakeSystemUi()` also dispatches keyboard insets. The keyboard appears automatically when your content has a focused text field, or you can force it with `showKeyboardByDefault = true`.

```kotlin
FakeSystemUi {
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  TextField(
    modifier = Modifier
      .imePadding()
      .focusRequester(focusRequester),
    state = rememberTextFieldState("…"),
  )
}
```

| Phone | Foldable |
| --- | --- |
| <img width="300" alt="me saket fakesystemui_FakeSystemUiTest_keyboard PhoneLight,GestureNavigation" src="https://github.com/user-attachments/assets/0a3c1524-f2fd-49ca-9388-a2266c501c31" /> | <img width="630" alt="me saket fakesystemui_FakeSystemUiTest_keyboard Foldable,GestureNavigation" src="https://github.com/user-attachments/assets/ac3a3a5e-05fc-425f-9bd2-6ed60426ca2d" /> |

## Custom system UI?

`FakeSystemUi()` covers the common system bars. For anything else, like display cutouts or the non-standard system bars on car infotainment systems, use `FakeSystemUiBuilder()` to render your own insets.
