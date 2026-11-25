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

## Running locally

```sh
./gradlew testDebugUnitTest                 # fast, run all the time
./gradlew connectedDebugAndroidTest         # needs a running emulator or a device
```

Reports land in `app/build/reports/`. CI runs both suites on every pull request.
