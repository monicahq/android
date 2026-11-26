# Accessibility

Accessibility is **mandatory** (constitution, principle VII). A screen that does not pass this page
is not done, whatever its tests say. The target is WCAG 2.2 AA applied to mobile.

## What every screen must do

| Rule | How in Compose |
| --- | --- |
| Every control has a name, a role and a state | Use Material components (`Button`, `IconButton`, `Switch`...), which bring role and state. A custom clickable uses `Modifier.clickable(role = Role.Button, onClickLabel = ...)` or `Modifier.semantics { role = ... }`. |
| Icon-only controls are named | `Icon(contentDescription = stringResource(R.string.x))` on the icon of an `IconButton`. |
| Decorative images are hidden | `contentDescription = null` (an image next to text that already says the same thing is decorative). |
| Meaningful images are described | `contentDescription = stringResource(...)`, describing what the image shows, not that it is an image. |
| Headings are headings | `Modifier.semantics { heading() }` on screen titles and section titles. |
| Grouped content reads as one item | `Modifier.semantics(mergeDescendants = true) {}` on a row whose parts only make sense together. |
| Reading order follows the visual order | Lay out in visual order; use `isTraversalGroup` / `traversalIndex` only when the layout cannot. |
| Touch targets of 48 × 48 dp | Material components already enforce it; a custom control uses `Modifier.minimumInteractiveComponentSize()`. |
| Contrast 4.5:1 for text, 3:1 for controls | Use only the theme colours (`MaterialTheme.colorScheme`, Monica tokens), never a raw colour. Check both light and dark. |
| Never colour alone | A state or an error also has text or an icon with a name. |
| Large font and display size | Sizes in `sp` for text, never fixed heights on text containers, `Modifier.verticalScroll` when content can outgrow the screen. Never scale text down. |
| Reduced motion | No animation is the only way to convey information; Compose animations follow the system animation scale. |
| Gesture alternatives | Swipe, long press and drag actions are also offered as a visible control or as `customActions` in semantics. |
| Language | Text comes from string resources so TalkBack reads it with the right voice. |

## Automated checks in tests

Every screen's UI test runs Android's Accessibility Test Framework (labels, contrast, touch target
size, traversal order):

```kotlin
// import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
@get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

@Test
fun passesAccessibilityChecks() {
    composeRule.enableAccessibilityChecks()
    composeRule.setContent { MonicaTheme { WelcomeScreen(/* ... */) } }
    composeRule.onRoot().tryPerformAccessibilityChecks()
}
```

Run it in light and dark theme (see `WelcomeScreenTest`). Android Lint's accessibility checks also run in CI.

## Manual checks before a pull request

Automated checks catch about a third of problems. For every screen a change touches:

1. **TalkBack**: swipe through the whole screen. Every element is announced with a sensible name,
   role and state, in visual order, nothing decorative is announced, and every action can be
   performed.
2. **Largest font and display size** (Settings > Accessibility > Display size and text): nothing is
   cut, overlapping or unreachable.
3. **Dark theme**: everything stays readable.
4. **Remove animations** (Settings > Accessibility): nothing important is lost.
5. **Switch Access or a keyboard** for screens with complex interactions: every action is
   reachable and the focus is visible.

The pull request says, in a bullet, which of these were done.
