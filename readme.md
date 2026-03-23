# fake system ui

`FakeSystemUi()` mimics Android's system UI by rendering fake status and navigation bars in screenshot tests. It dispatches real window insets so your layouts behave correctly when they're inset-dependent.

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
