# Research: Welcome Screen

## R1. ViewModel or not

- **Decision**: no ViewModel. `MainActivity` calls a stateless `WelcomeScreen(instanceHost,
  onSignUp, onLogIn, onInstanceSettings)` with no-op lambdas and the host of the default instance.
- **Rationale**: the screen has no state, no data source and no event to handle (FR-005).
  Principle I forbids an abstraction without a consumer; principle II's split is respected because
  the screen is already the stateless half, and the stateful half (route + ViewModel) arrives with
  the instance sheet, the first feature that has state.
- **Alternatives considered**: a `WelcomeViewModel` exposing `StateFlow<WelcomeUiState>` with the
  host: rejected, it would hold a constant.

## R2. Colours: Monica palette instead of dynamic colour

- **Decision**: remove dynamic colour. Define the Monica tokens (`--page`, `--ink`, `--muted`,
  `--primary`, `--on-primary`, `--hairline`, `--hairline-strong`, `--card`) in `Color.kt` for
  light and dark, and map them onto Material 3 roles:

  | Monica token | Material 3 role | Light | Dark |
  | --- | --- | --- | --- |
  | page / canvas | `background`, `surface` | `#FFFFFF` | `#09090B` |
  | ink | `onBackground`, `onSurface` | `#09090B` | `#FAFAFA` |
  | muted | `onSurfaceVariant` | `#71717A` | `#A1A1AA` |
  | primary | `primary` | `#18181B` | `#FAFAFA` |
  | on-primary | `onPrimary` | `#FAFAFA` | `#18181B` |
  | hairline-strong | `outline` | `#D4D4D8` | `#3F3F46` |
  | hairline | `outlineVariant` | `#E4E4E7` | `#27272A` |
  | card | `surfaceContainer*` | `#FAFAFA` | `#18181B` |

- **Rationale**: the design system has no accent colour; wallpaper-based dynamic colour would
  contradict it. Mapping onto Material roles lets Material components (buttons, ripple, focus) use
  the right colours without a parallel colour system. Contrast of these pairs is measured in the
  design system (ink, muted ≥ 4.5:1 on page in both themes).
- **Alternatives considered**: a custom `CompositionLocal` of Monica colours: rejected for now
  (Principle I), Material roles cover every colour this screen needs.

## R3. Typography

- **Decision**: keep the system sans (Roboto on Android, the design system's "system sans" stack).
  Type values from the design, in `sp`:
  title 28/34 SemiBold, letter spacing -0.03em; wordmark 30 SemiBold, -0.03em; description 16/24
  in `onSurfaceVariant`; buttons 16 Medium; instance line 13.5/20 with the host in `onSurface`
  Medium. Mapped on Material roles `headlineMedium` (title), `bodyLarge` (description),
  `labelLarge` (buttons, raised to 16 sp to match the design), `bodySmall` (instance line).
- **Rationale**: no webfont is a design system rule; `sp` keeps font scaling (principle VII).
- **Alternatives considered**: bundling Inter or Geist: rejected by the design system.

## R4. Images and icons

- **Decision**:
  - `assets/logo.svg` → `res/drawable/ic_logo.xml` vector drawable, colours kept (the mark is
    never recoloured). Decorative (`contentDescription = null`), since "Monica" is written next to
    it.
  - Monica `settings` glyph (16×16, stroke 1.4, round caps) → `ic_settings.xml`, tinted with
    `onSurface`, shown at 22 dp inside a 48 dp `IconButton`.
  - The instance glyph drawn inline in the design → `ic_instance.xml`, 16 dp, decorative.
  - `assets/panda-welcome.png` → `res/drawable-nodpi/panda_welcome.webp` (WebP quality 90, as Android
    Lint recommends; 69 KB instead of 470 KB lossless), full width, aspect ratio 880:530, `ContentScale.Crop`, edges faded with an
    elliptical radial gradient mask (72% × 70%, opaque to 62%) drawn with `BlendMode.DstIn` in an
    offscreen layer.
- **Rationale**: vectors stay sharp at every density and follow the theme tint; one bitmap only.
- **Alternatives considered**: an image library (Coil) or loading the SVG at runtime: rejected,
  nothing remote to load.

## R5. Layout that fills tall screens and scrolls on small ones

- **Decision**: a `Column` with `verticalScroll` whose minimum height is the viewport height
  (`BoxWithConstraints` + `heightIn(min = maxHeight)`), split into a top group (settings button,
  logo, illustration, texts) and a bottom group (buttons, instance line) with
  `Arrangement.SpaceBetween`. System bars handled with `safeDrawingPadding()` after
  `enableEdgeToEdge()`.
- **Rationale**: matches the design's `flex:1` spacer on tall screens and meets FR-012 (scroll, no
  clipping) at large font sizes and in landscape. `Modifier.weight` does not work inside a
  scrolling column.

## R6. Languages and per-app language

- **Decision**: English in `res/values/strings.xml`, French in `res/values-fr/strings.xml`.
  `androidResources { generateLocaleConfig = true }` in `app/build.gradle.kts` with
  `res/resources.properties` containing `unqualifiedResLocale=en-US`, so Android 13+ lists English
  and French in the app's language setting. The instance line is one format string
  (`Instance: %1$s` / `Instance : %1$s`, with the French non-breaking space) and the host is styled
  by locating the argument in the formatted text.
- **Rationale**: standard Android resource fallback gives English for any other language (FR-007);
  the generated locale config cannot drift from the folders that exist (FR-009). One format string
  lets a future language move the host.
- **Alternatives considered**: AppCompat's `setApplicationLocales`: not needed without an in-app
  picker, and adds a dependency.

## R7. Keeping languages in step

- **Decision**: a JVM unit test (`StringResourcesParityTest`) parses every
  `res/values*/strings.xml` and fails when a key is missing in a language, or when a format
  string's placeholders differ.
- **Rationale**: principle VI requires every language to be updated in the same pull request; this
  makes CI enforce it. Android Lint's `MissingTranslation` only covers part of it and can be
  silenced per string.

## R8. Testing a screen in French and in large fonts

- **Decision**: UI tests wrap the screen in `CompositionLocalProvider` with a `LocalContext` and
  `LocalConfiguration` built by `createConfigurationContext()` for `Locale.FRENCH`, and with a
  `LocalDensity` whose `fontScale` is 2 for the large-font test.
- **Rationale**: no need to change the emulator's locale or settings; tests stay independent and
  fast.

## R9. Automated accessibility checks

- **Decision**: add `androidx.compose.ui:ui-test-junit4-accessibility` (version from the BOM) to
  `androidTestImplementation`; screen tests use `createAndroidComposeRule<ComponentActivity>()`,
  call `enableAccessibilityChecks()` and `onRoot().tryPerformAccessibilityChecks()` in light and
  dark.
- **Rationale**: required by principle VII and `docs/accessibility.md`; it checks labels, contrast,
  touch target size and traversal order with the same framework as Accessibility Scanner.

## R10. Coverage gate (Kover)

- **Decision**: apply the Kover Gradle plugin 0.9.11 to `:app`; exclude UI code from the measure
  (classes annotated `@Composable`, `@Preview`, `*Screen.kt` files, whose composable lambdas Kover
  cannot tell from logic, `MainActivity`, the `ui.theme` package, generated
  `R` / `BuildConfig` / `ComposableSingletons` classes); fail `koverVerifyDebug` under 80% line
  coverage. The CI "Unit tests" job runs `testDebugUnitTest koverVerifyDebug` and uploads the HTML
  report, so the required checks of the `main` ruleset do not change.
- **Rationale**: closes the constitution's `TODO(COVERAGE_GATE)` ("configure Kover with the first
  feature"). UI code is covered by UI tests, which Kover's JVM measure cannot see.
- **Alternatives considered**: a separate "Coverage" CI job: rejected, it would need a ruleset
  change for no benefit.
