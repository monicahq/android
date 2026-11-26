# Testing

Every change ships with tests. Two kinds are mandatory (see the constitution, principles III and IV).

| Kind | Location | Runs on | Covers | Command |
|------|----------|---------|--------|---------|
| Unit | `app/src/test` | JVM | ViewModels, repositories, mappers, any non-UI logic | `./gradlew testDebugUnitTest` |
| UI | `app/src/androidTest` | Emulator/device | Every screen's states and interactions, user journeys | `./gradlew connectedDebugAndroidTest` |

## Unit tests

- One test class per production class, same package.
- Use **fakes** (simple in-memory implementations), not mocking frameworks.
- Use `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`) and inject the test dispatcher.
- Test every `UiState` a ViewModel can emit and every event it handles.
- Fixing a bug? Write the failing test first.

```kotlin
class ContactsViewModelTest {
    @Test
    fun `emits Success when the repository returns contacts`() = runTest {
        val repository = FakeContactsRepository(contacts = listOf(aContact()))
        val viewModel = ContactsViewModel(repository)

        assertEquals(ContactsUiState.Success(listOf(aContact())), viewModel.uiState.first { it !is ContactsUiState.Loading })
    }
}
```

## UI tests

- Test the **stateless** screen composable: give it a `UiState`, assert what is shown, trigger
  interactions, check the callbacks.
- Find nodes by visible text or semantics (`onNodeWithText`, `onNodeWithContentDescription`).
  Use `testTag` only as a last resort.
- Each user journey from a spec gets at least one end-to-end test through `MainActivity`.
- Each screen has a test running the automated accessibility checks
  (`enableAccessibilityChecks()`), see [accessibility.md](accessibility.md).

```kotlin
class ContactsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun showsContactNames() {
        composeRule.setContent {
            MonicaTheme { ContactsScreen(ContactsUiState.Success(listOf(aContact(name = "Ada"))), onContactClick = {}) }
        }
        composeRule.onNodeWithText("Ada").assertIsDisplayed()
    }
}
```

## Coverage

Kover measures the unit tests' line coverage of non-UI code and fails under 80%
(`app/build.gradle.kts`). Composables, `*Screen.kt` files, previews, `MainActivity` and the theme
are excluded: they are covered by the UI tests. So keep logic out of screen files: a pure function
a screen needs (like `welcome/Highlight.kt`) goes in its own file, with its own unit test.

## Languages

`StringResourcesParityTest` fails when a key or a placeholder is missing in one of the
`values-*/strings.xml` files. UI tests show a screen in another language without changing the
device's by providing a `LocalContext` and `LocalConfiguration` for that locale (see
`WelcomeScreenTest`).

## Running locally

```sh
./gradlew testDebugUnitTest                 # fast, run all the time
./gradlew koverVerifyDebug                  # coverage gate; koverHtmlReportDebug for the report
./gradlew connectedDebugAndroidTest         # needs a running emulator or a device
```

Reports land in `app/build/reports/`. CI runs both suites on every pull request.
