# Screenshot Testing Strategy — NotifyBridge

Status: **implementation-ready** (tightened after code review, 2026-07-20).

Legend: **[V]** verified against source · **[R]** recommendation · **[?]** needs confirmation.

---

## 1. Current state (verified)

| Area | Finding |
|---|---|
| **Module layout** [V] | Single Gradle module `:app` + `build-logic` convention plugins. No `:core`/`:designsystem`/`:feature` split. |
| **Stack** [V] | AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, minSdk 26 / compileSdk 35, JVM 17. |
| **Theme** [V] | `NotifyBridgeTheme` (`ui/theme/Theme.kt`) is **dark-only** — one `darkColorScheme`, no light scheme, no dynamic color, no `isSystemInDarkTheme()`. |
| **Screens** [V] | 7 Compose screens: `ui/{status,apps,broker,permissions,onboarding,locked,language}`. |
| **Previews** [V] | **16 `@Preview` annotations** = 15 across the 7 screens + 1 in `localization/LocalLanguage.kt`. `StatusScreen.kt` has 3 (`Connected + activity`, `Disconnected + empty`, `Redacted recent`). |
| **Compose structure** [V] | Stateless content pattern — each screen has a `*Content(...)` composable fed by plain params (`StatusContent` `StatusScreen.kt:152`, `BrokerContent:181`, `AppsContent:95`, `OnboardingContent:106`, `PermissionsContent:170`). |
| **Visibility problem** [V] | **All `*Content` composables are `private`** → `app/src/test` (same module) cannot call them. Public `*Screen(viewModel)` wrappers pull in Hilt/Nav/side-effects. **This is a prerequisite to fix.** |
| **Reusable components** [V] | `SectionCard`, `RecentRow`, `RecentAppIcon(icon: Drawable?)`, `StatusBottomNav` exist but are `private` and inline — no shared design-system package. |
| **Existing UI test** [V] | One instrumented test: `androidTest/.../locked/LockedScreenTest.kt` — `createComposeRule()` + `CompositionLocalProvider(LocalLanguage provides …)`. |
| **Robolectric** [V] | `testImplementation(libs.robolectric)` (4.14); **5 JVM tests already use `RobolectricTestRunner`/`@Config`**. `testOptions { unitTests { isIncludeAndroidResources = true } }` set (`app/build.gradle.kts:45`). |
| **Localization** [V] | `LocalLanguage` CompositionLocal **defaults to `KeyEchoLanguage`** (renders `dictionary.key`, not real text) when no `Localized` wrapper is present — `LocalLanguage.kt:8` + its KDoc. Real text needs `AssetJsonLanguage(tag, context)`. |
| **Determinism sources** [V] | Number/time formatting uses default locale/timezone (`StatusScreen.kt:275`, `:403`); app icons loaded async via `PackageManager` (`rememberAppIcon` `StatusScreen.kt:461`). |
| **Image loading** [V] | No Coil/Glide. Icons drawn via Canvas from local `PackageManager` drawables. No remote artwork. |
| **CI** [V] | `ci.yml` → `build-and-test` on `ubuntu-latest`, JVM 17: `assembleDebug testDebugUnitTest detekt lintDebug` (`:37`). Separate `instrumented.yml` runs an API-28 emulator (~7 min). Required-check enforcement is **not** in this file (needs branch protection). |

**Bottom line:** the JVM + Robolectric + android-resources harness is already in place and in use. The gaps are (a) private entrypoints, (b) a real Language provider in tests, and (c) explicit determinism pinning.

## 2. Missing prerequisites

1. **Promote entrypoints** [V]: make each `*Content` composable `internal` (preferred — minimal, keeps them out of the public API) **or** add thin `@VisibleForTesting` screenshot-fixture wrappers. Without this, `app/src/test` cannot render them.
2. **Real English Language in tests** [V]: tests must wrap content in a real `AssetJsonLanguage("en", context)`. The default `KeyEchoLanguage` renders keys — useless for text/large-font regression. Previews are therefore **not** reusable as baselines unmodified.
3. **Determinism seams** [V]: pin `Locale` + `TimeZone`, pass a fixed instant into time-formatting, and inject a fixed placeholder `Drawable` via the existing `RecentAppIcon(icon: Drawable?)` param (never read installed-app icons).
4. **Screenshot library + Gradle plugin** — none present.
5. **CI record/verify step + baseline location.**
6. **Shared test harness** factoring the theme + Language + config setup (template = `LockedScreenTest`).

No architectural change required.

## 3. Framework comparison & recommendation

| Framework | Runs on | Fit | Cost / limits |
|---|---|---|---|
| **Roborazzi** [R ✅] | JVM via Robolectric (already a dep, already used) | **Best.** Real `Context` → asset localization + PackageManager work unchanged. Slots into the existing `testDebugUnitTest` job; no emulator; runs on Intel Mac. | Robolectric native rendering ≠ real-device pixels; **cross-OS pixel diffs** → baselines recorded on one authoritative host. |
| **Paparazzi** | JVM via bundled LayoutLib | Good for pure previews; more friction with real-`Context` paths this app uses; **pins to specific AGP/LayoutLib** (upgrade coupling — cf. the AGP-version lint break already hit). | No real framework; version-locked. |
| **AGP Preview Screenshot Test** (`enableScreenshotTest`) | JVM (Layoutlib) | Lowest boilerplate (consumes `@Preview`s) — but previews render *keys*, so still needs the Language fix. | **Experimental/alpha** in AGP 8.7; not safe to gate CI on yet. |
| **Emulator + `captureToImage`** | Instrumentation (API-28) | Highest fidelity; reuses `instrumented.yml`. | Slow (~7 min), flakier, font/device-dependent. Overkill for static screens. |

**Recommendation [R]: Roborazzi**, as JVM Robolectric unit tests — because Robolectric, `isIncludeAndroidResources`, and the JVM test job already exist and are in use; it adds visual coverage with no emulator cost. Revisit the AGP built-in feature when it stabilizes.

## 4. Test & preview architecture

- **Target the `*Content` composables** (once `internal`), not the `*Screen(viewModel)` wrappers.
- **One shared harness** wrapping content in `NotifyBridgeTheme { CompositionLocalProvider(LocalLanguage provides realEnglish) { … } }`, with `Locale`/`TimeZone` pinned.
- **Previews enumerate states, but tests supply the real Language** — treat previews as a state checklist, not as baseline sources.

## 5. Coverage criteria — when something earns a screenshot

Dedicated coverage only if **≥2** hold: business importance · visual complexity · **≥3 variants** · reuse across ≥2 screens · **regression history** · font-scale/responsiveness risk.

- **Screen-level is sufficient** when a component appears in one screen with no independent variants (e.g. `SectionCard`).
- **Component-level is warranted** only when a component is promoted to a shared package **and** reused. No component qualifies today → **do not** test private inline composables individually.
- **Status qualifies first**: business-critical, 4 chip states, and documented regression history (PR #5, `depth()`→`pendingCount()`).

## 6. Coverage strategy split

1. **Full screens — primary coverage now** (all 7 have previews).
2. **Reusable components — deferred** until extraction.
3. **Shared design-system components — none exist** (theme = tokens, not components).
4. **States/variants — from the real state enums**, providing a real Language.

**More previews needed?** [R] Targeted only: add **CONNECTING** and **ERROR** Status variants (see §7); one **1.5× large-font** variant for Status + Permissions; fill any missing empty/error state per screen **[?]**. No previews for private sub-composables.

## 7. Test scenarios — deliberately small matrix

**Include:**
- **Theme:** dark only — no light theme exists [V] (axis size 1).
- **Device:** one phone spec, portrait, default density.
- **Status states:** cover all 4 `ConnectionState` chips — **CONNECTED, CONNECTING, ERROR, DISCONNECTED** [V] (existing previews only cover connected/disconnected + redacted; add CONNECTING + ERROR). Plus the `Redacted recent` variant.
- **Other screens:** their real empty/populated/error states.
- **Font scale:** one 1.5× variant on Status + Permissions only.

**Exclude:** light/dark pair (no light theme); tablet/TV/landscape [?]; full locale × screen grid (pick one long-text locale, e.g. FR/ES, on one text-dense screen); per-component micro-states.

**Nondeterminism handling:**
- **Locale + timezone** [V]: pin both (Robolectric `@Config` qualifiers + `Locale.setDefault`/`TimeZone.setDefault`), because `StatusScreen.kt:275/:403` format with defaults.
- **App icons** [V]: inject a fixed `Drawable` via `RecentAppIcon(icon: Drawable?)`; never read `PackageManager`.
- **Timestamps**: pass a fixed instant into content.
- **Async / remote images / animations**: render static `*Content` state; no network images exist; snapshot settled frames.
- **Fonts / density / platform**: record baselines on the Linux CI host (or pinned Docker) to avoid Mac-vs-Linux drift.

## 8. Example test cases (real components)

Shared harness — `app/src/test/java/com/nyasa/notifybridge/screenshot/ScreenshotTest.kt`:

```kotlin
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
abstract class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun pinEnv() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Dublin"))
    }

    // Real English (NOT the KeyEchoLanguage default), lifted from LockedScreenTest.
    protected fun content(body: @Composable () -> Unit) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val en = AssetJsonLanguage(tag = "en", context = ctx)
        compose.setContent {
            CompositionLocalProvider(LocalLanguage provides en) {
                NotifyBridgeTheme { body() }
            }
        }
    }
}
```

Status — one baseline per real chip state (requires `StatusContent` promoted to `internal`):

```kotlin
class StatusScreenScreenshotTest : ScreenshotTest() {
    @Test fun connected_withActivity() {
        content { StatusContent(state = FakeStatus.connected, icon = StubIcon) }
        compose.onRoot().captureRoboImage()
    }
    @Test fun connecting()    { content { StatusContent(state = FakeStatus.connecting) }; compose.onRoot().captureRoboImage() }
    @Test fun error()         { content { StatusContent(state = FakeStatus.error) };      compose.onRoot().captureRoboImage() }
    @Test fun disconnected()  { content { StatusContent(state = FakeStatus.disconnected) }; compose.onRoot().captureRoboImage() }
    @Test fun redacted()      { content { StatusContent(state = FakeStatus.redacted) };    compose.onRoot().captureRoboImage() }
}
```

`FakeStatus` / `StubIcon`: extract the fixtures the existing `@Preview`s already build into a shared test fixture, so preview and screenshot share one source of truth.

## 9. Gradle & CI changes

**`gradle/libs.versions.toml`** — add Roborazzi version, the `roborazzi` / `roborazzi-compose` / `roborazzi-junit-rule` libs, and the plugin (pin to the release matching Compose BOM 2024.12 / AGP 8.7).

**`app/build.gradle.kts`** — apply the plugin; add the three libs as `testImplementation`. `isIncludeAndroidResources` and `robolectric` already present. Promote `*Content` composables to `internal`.

**`ci.yml`** (`build-and-test` job, no new job/emulator):
```
./gradlew assembleDebug testDebugUnitTest verifyRoborazziDebug detekt lintDebug --continue …
```
Upload `app/build/outputs/roborazzi/**` (diff images) on failure alongside existing reports.

## 10. Baseline storage & update workflow

- Baselines committed under `app/src/test/screenshots/` — small (one device, dark only, real states).
- **Authoritative host = Linux CI** to avoid font drift. Phase 1: developer runs `./gradlew recordRoborazziDebug`, commits; CI `verify` gates. Phase 2+: switch to a CI "record" dispatch that commits Linux-rendered baselines.
- Intentional UI change → re-record → commit new PNGs **in the same PR** as the code.

## 11. PR review & approval

- CI attaches actual/expected/diff PNGs on `verify` failure; reviewer confirms intent before approving baseline updates.
- **Rule:** baseline PNG changes must accompany a code change in the same PR; a baseline-only rewrite is a red flag (possible masked regression).
- Add a PR checkbox: "Screenshot baseline changes are intentional and reviewed."

## 12. Risks, ownership, maintenance

- **Cross-host rendering drift** — top risk; owned by CI/Docker-recorded baselines.
- **Version coupling** — pin Roborazzi to the BOM; bump deliberately.
- **Baseline churn** — bounded by the small matrix (dark, one device, real states).
- **Ownership** [?] — `ui/` owner; screenshot failures block merge like any gate.
- **Required-check enforcement** [V] — set via **branch protection** (admin, outside `ci.yml`).

## Rollout plan

| Phase | Scope | Outcome | Success criteria |
|---|---|---|---|
| **1 — PoC** | Wire Roborazzi; promote `StatusContent` to `internal`; harness with real English + pinned locale/tz; StatusScreen 5 states (incl. CONNECTING, ERROR). | Green `verifyRoborazziDebug` locally + CI. | 1 screen, ~5 baselines, CI gate, <~30s added to JVM job. |
| **2 — High-risk UI** | Locked, Permissions, Onboarding; 1 large-font variant (Status, Permissions); 1 long-text locale on one screen. | Security/setup surfaces covered. | ≤~15 baselines; no flakes over 10 CI runs. |
| **3 — Enforcement & breadth** | Apps, Broker, Language; CI-recorded baselines; branch protection makes `verify` required. | Full screen-level net; deterministic baselines. | All 7 screens; required check on PRs. |
| **4 — Ongoing** | Component tests only on promotion to shared package; quarterly matrix review; version bumps with BOM. | Stable, low-maintenance suite. | Matrix growth tied to real reuse/regressions. |

## Final recommendation

- **Framework:** Roborazzi (JVM + Robolectric) — reuses existing, in-use infrastructure; no emulator cost; works on Intel Mac.
- **Where:** `app/src/test/.../screenshot/`, baselines in `app/src/test/screenshots/`, verified in the existing `build-and-test` job.
- **Previews:** mostly sufficient — add CONNECTING + ERROR Status variants and a large-font variant for 2 text-dense screens; not for private sub-composables. Remember previews render keys, tests render real English.
- **Test first:** StatusScreen (business-critical, 4 chip states, PR #5 regression history).
- **Deliberately don't test:** light theme (none), tablet/TV/landscape, private inline components, full locale × screen grid.
- **Why:** maximum regression protection per baseline and per CI-second, with the two known correctness traps (private entrypoints, key-echo localization) handled up front.

### Open items to confirm [?]
1. No tablet/landscape intent.
2. Per-screen state enums for empty/error preview gaps.
3. Any infinite animations in the render path.
4. Phase-1 baseline-record ownership (local vs CI-recorded).

---

# Execution log

Record of how each phase was actually carried out (the sections above are the
plan; this is what shipped). Newest last.

## Phase 1 — Status PoC — merged 2026-07-20 (PR #6, commit 08a4924)

**Goal:** prove Roborazzi end-to-end on one screen and gate CI on it.

**Steps taken:**
1. Added Roborazzi `1.32.0` to the version catalog (3 libs + plugin); applied the
   plugin in root (`apply false`) + `:app`; added the Compose BOM to the `test`
   config so `ui-test-junit4` resolves on the JVM.
2. Promoted `StatusContent` `private → internal` (the public `StatusScreen(nav)`
   pulls in Hilt/Nav and isn't renderable from `test/`).
3. Wrote the shared `ScreenshotTest` harness: `@RunWith(RobolectricTestRunner)`,
   `@GraphicsMode(NATIVE)`, `@Config(Pixel5, sdk 34)`; pins `Locale.US` +
   `Europe/Dublin`; injects a real English `AssetJsonLanguage` (the default
   `LocalLanguage` = `KeyEchoLanguage` renders i18n keys, not text).
4. `StatusScreenScreenshotTest`: 5 baselines — all four `ConnectionState` chips
   (CONNECTED/CONNECTING/ERROR/DISCONNECTED) + redacted-recent. Fixtures mirror the
   existing `@Preview` data.
5. `recordRoborazziDebug` → committed 5 PNGs under `app/src/test/screenshots/`.
6. Wired `verifyRoborazziDebug` into the CI `build-and-test` gradle line; upload
   `roborazzi/` diff images on failure.

**Gotchas hit:**
- Promoting to `internal` changed the detekt `LongMethod` **baseline ID** (IDs embed
  the full signature) → the finding resurfaced. Fixed by editing the ID string in
  both `config/detekt/baseline*.xml`. (Same class of fragility as import-order IDs.)
- Long `RecentItem(...)` fixture lines tripped `ArgumentListWrapping` → wrapped per-arg.

**Determinism:** times render from fixed `postTime` + pinned locale/tz; icons resolve
to null under Robolectric (packages not installed) → deterministic fallback; no
network images in the app.

**Outcome:** all local gates green (~14s added, no emulator). **Key finding:** the
macOS-recorded baselines **passed unchanged on Linux CI** — cross-platform font
rendering was stable, so no CI-side re-recording was needed.

## Phase 2 — High-risk UI + variants — merged 2026-07-22 (PR #7, commit 6166af5)

**Goal:** cover the setup/security screens and add the two cross-cutting checks,
reusing the Phase-1 harness with no new infrastructure.

**Steps taken:**
1. Promoted `PermissionsContent` and `OnboardingContent` `private → internal`.
   (`LockedScreen(onUnlock)` was already public — rendered directly, no promotion.)
2. Generalized `ScreenshotTest.setScreen` with `languageTag` (default `en`) and
   `fontScale` (default `1f`) knobs; font scaling via
   `LocalDensity provides Density(base.density, fontScale)`.
3. New tests/baselines: Locked (1: unlock prompt), Onboarding (3: GRANT_ACCESS /
   CONNECT_BROKER / CHOOSE_APPS), Permissions (2: all-granted / action-needed).
4. Cross-cutting variants: 1.5× large-font on Status + Permissions; French on the
   settings-dense Permissions screen (verified `fr/strings.json` exists).
5. `recordRoborazziDebug` → **14 baselines total** (was 5).

**Gotchas hit:**
- Same detekt baseline-ID break for the two promoted content fns → refreshed IDs.
- The Permissions test helper hit `LongParameterList` (>6 params) → collapsed the
  three permission-state params into one `granted: Boolean` (maps to the two preview
  states), then `MultiLineIfElse` required braces on the resulting if/else.

**Outcome:** all local gates green; visually confirmed French renders real localised
copy (not key-echo) and large-font surfaces wrapping/clipping. All 9 new Mac-recorded
baselines again verified unchanged on Linux CI.

## Phase 3 — Remaining screens — <branch feat/screenshot-testing-phase3>

**Goal:** complete screen-level coverage (Apps, Broker, Language) and settle the
two operational switches from the plan.

**Steps taken:**
1. Promoted `AppsContent`, `BrokerContent`, `LanguageSettingsContent`
   `private → internal`; refreshed their detekt `LongMethod` baseline IDs.
2. New tests/baselines: Apps (populated / empty), Broker (empty /
   filled+TLS+connected), Language (system-default / French). Fixtures mirror the
   existing `@Preview` data; `icons = emptyMap()` keeps Apps deterministic.
3. `recordRoborazziDebug` → **20 baselines total** (was 14). Full local gate green.

**Operational-switch decisions:**
- **CI-side baseline recording — deliberately skipped.** Phases 1–2 proved Mac↔Linux
  rendering is stable (baselines recorded locally verified unchanged on CI), so
  record-local / verify-CI is sufficient. Building a CI-record workflow would be
  infrastructure we don't need; revisit only if a future run diffs on fonts.
- **Branch protection — enabled 2026-07-23.** `main` now requires the
  "Build, unit tests, detekt, lint" status check (which runs `verifyRoborazziDebug`),
  so a PR that changes any screen's pixels can't merge until baselines are updated.
  "Android instrumented tests (API 28)" is deliberately NOT required (flaky emulator),
  and admin bypass is left on for emergencies. Force-push and deletion of `main` are
  disabled; a pull request is required to merge.

**Outcome:** all 7 screens now have screen-level baselines (20 total), gating every PR
via the existing JVM job.

## Recurring lessons

- **detekt/lint baselines are signature/import-string keyed.** Any visibility,
  signature, or import edit to a baselined symbol breaks its suppression; refresh the
  ID string in both `baseline.xml` and `baseline-debug.xml` (surgical `sed`, verifying
  the token is unique first).
- **Record locally, verify on CI works** for this project — Mac↔Linux rendering has
  been stable across both phases. Revisit only if a future run diffs on fonts.
- **Adding a screen is now mechanical:** promote its `*Content` to `internal`,
  subclass `ScreenshotTest`, `captureRoboImage(...)`, `recordRoborazziDebug`, commit.
