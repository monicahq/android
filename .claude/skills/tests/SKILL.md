---
name: tests
description: Conventions for writing unit tests and Compose UI tests in this Android app. Use when the user wants to create or modify tests, including test structure, naming conventions, fakes, coroutines and UI tests.
---

# Tests

- You MUST read and follow `docs/testing.md` and principles III and IV of
  `.specify/memory/constitution.md`.
- Unit tests live in `app/src/test`, UI tests in `app/src/androidTest`, in the same package as the
  class they test. A test class is named after the class it tests: `ContactsViewModelTest`,
  `ContactsScreenTest`.
- Test names describe the behaviour with backticks:
  `` fun `emits Error when the repository fails`() ``. For instrumented tests, where backticks with
  spaces are not allowed on older APIs, use camelCase: `showsErrorMessageWhenLoadingFails`.
- One behaviour per test, structured as arrange / act / assert, separated by a blank line.
- You MUST use fakes (hand-written in-memory implementations of the repository or data source
  interface) instead of mocking frameworks. Fakes live in the test source set, next to the tests.
- Coroutines: use `runTest` and inject a `StandardTestDispatcher` / `UnconfinedTestDispatcher`.
  Set `Dispatchers.Main` with a JUnit rule when testing a `ViewModel`. Never use `Thread.sleep`
  or real delays.
- A `ViewModel` test covers every `UiState` it can emit and every event it handles.
- UI tests target the stateless screen composable with `createComposeRule()`, wrapped in
  `MonicaTheme`. Find nodes by text or semantics; `testTag` only as a last resort. Assert what a
  person sees and the callbacks that fire, never colors, sizes, padding or other styling.
- Read strings in UI tests from resources
  (`InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.x)`) rather than
  duplicating the English text, so tests survive copy changes.
- You MUST NOT test generated code, Android framework behaviour, or trivial data classes with no
  logic of their own.
- A bug fix starts with a test that fails without the fix.
