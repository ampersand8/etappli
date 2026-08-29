---
description: Scaffold a new screen end-to-end — route, NavHost entry, ViewModel with container factory, Compose screen, and the tests both gates demand. Use when adding a screen, destination, or navigation flow.
---

# New screen recipe

Follow existing screens (`ui/settings/` is the simplest full example). Steps:

## 1. Route — `ui/nav/Routes.kt`

`@Serializable` object (no args) or data class (args as constructor params, optional
args defaulted to null). Nothing else — no route strings anywhere.

## 2. ViewModel — `ui/<feature>/<Name>ViewModel.kt`

- Constructor takes repositories (interfaces from `data/`), never the container.
- State: private `MutableStateFlow<UiState>` + public `StateFlow`; derived values as
  `get()` properties on the UiState data class (e.g. `canSave`).
- Route args come from `SavedStateHandle.toRoute<MyRoute>()`.
- Companion `Factory = containerViewModelFactory { container -> … }`; use
  `createSavedStateHandle()` for route args.
- Repository writes inside `viewModelScope.launch`; navigation side effects via
  callback parameters (`onSaved: () -> Unit`), never held in state.

## 3. Screen — `ui/<feature>/<Name>Screen.kt`

- Signature: navigation callbacks first, then
  `viewModel: X = viewModel(factory = X.Factory)` last (tests pass their own).
- Collect state with `collectAsStateWithLifecycle()`.
- Money: `formatCurrency(amount, settings.currency)`; dates: `formatDate`/
  `formatDateRange` (`ui/Format.kt`); decimal/date inputs: `DecimalField`/`DateField`
  + `parseDecimal` (`ui/components/Fields.kt`).

## 4. Wire up — `ui/nav/AppNavHost.kt`

`composable<MyRoute> { … }` with navigation lambdas calling `navController`. A result
returned to the previous screen goes through
`navController.previousBackStackEntry?.savedStateHandle` (see `PICKED_LOCATION_KEY`).

## 5. Tests (both gates are hard failures)

- ViewModel test: plain JUnit, `MainDispatcherRule`, Turbine; in-memory repos with
  `seed = false`. Route args via
  `SavedStateHandle(mapOf(...))` — copy an existing ViewModel test's setup.
- Screen test: `createComposeRule` + `@RunWith(AndroidJUnit4::class)` +
  `@Config(application = TestCamperApp::class)`; construct the ViewModel yourself and
  record navigation callbacks into a list. If the screen embeds a map, wrap content in
  `CompositionLocalProvider(LocalMapEnabled provides false)`.
- Add a navigation-flow assertion to `AppNavHostTest` if the screen is reachable from
  an existing one.
- 100% line coverage: `./gradlew :app:coverageVerify`. Device-only code goes in
  `coverageExcludes` (app/build.gradle.kts) instead — sparingly.
- If the ViewModel is JVM-pure (no Robolectric in its tests), add it to the pitest
  `--targetClasses` and `--targetTests` lists in app/build.gradle.kts and keep
  `./gradlew :app:pitest` ≥ 80%.

## 6. Verify

`./gradlew test :app:coverageVerify` (+ `:app:pitest` if step 5 touched it). Then the
**verify-app** skill for a real walk-through if navigation or maps changed.
