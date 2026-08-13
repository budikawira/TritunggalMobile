# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## What this is

`Source/mobile` holds the Android handheld client for the AREI/Tritunggal RFID inventory system, plus vendor/reference material it depends on:

- **`TritunggalMobile/`** — the active app (git repo, `github.com/budikawira/TritunggalMobile`). This is what the rest of this file documents.
- **`moie/`** — a separate, earlier standalone app (`namespace com.vidi.rfid`) by the same author. No git relation to `TritunggalMobile`, not built as part of this project — reference only.
- **`Demo-uhf-ble_as/uhf-ble-demo/`** (extracted from `Demo-uhf-ble_as.rar`) — the RFID reader vendor's official SDK demo app. Reference for `com.rscja.deviceapi` usage, not part of the shipped app.
- **`DeviceAPI_ver20230228_release.aar`** — the same RFID SDK vendored into `TritunggalMobile/app/libs/`; the copy here is just a spare/reference copy.
- **`tri_1.2.2.apk`, `tri_1.2.3.apk`** — built release artifacts checked in for distribution. Build output, not source; don't edit.

Kotlin/Android app, package `com.inventory.app.mobile`, single module (`app`). It's a handheld client for RFID gate/inventory operations (pairing, placement, transfer, shipment, stock opname, item registration, find) that talks to `TritunggalWeb.Api` (the sibling `.NET` API — see `../../TritunggalWeb/CLAUDE.md`).

## Commands

```powershell
cd TritunggalMobile
./gradlew assembleDebug
./gradlew assembleRelease     # signed with app/uhf-serial_release.jks
./gradlew installDebug        # requires a connected device/emulator
```

`applicationId = "com.inventory.app.mobile"`, current `versionName = "1.2.3"` / `versionCode = 10203` in `app/build.gradle.kts` — bump both when cutting a release, matching the naming of the checked-in `tri_1.2.x.apk` files in the parent folder.

**No real test coverage** — `app/src/test` and `app/src/androidTest` only contain the default `ExampleUnitTest`/`ExampleInstrumentedTest` boilerplate. Don't suggest running `./gradlew test` as if it verifies behavior.

## App structure

- **`AppCtx`** — the `Application` class; also the file holding shared top-level `const val`s (request codes, UHF flag constants) used across activities/fragments.
- **Activities**: `LoginActivity` (launcher), `MainActivity` (hosts the Navigation-Component graph for all feature screens), `SetupActivity` (server URL / RFID power config), `DeviceListActivity` (Bluetooth device picker for pairing), `DialogListActivity` (generic picker dialog).
- **`BaseFragment`** — every feature fragment extends this. It owns: the `RFIDWithUHFBLE` singleton and its connect/disconnect/reconnect lifecycle, Bluetooth permission/adapter handling, an auto-disconnect timer, `SweetAlertDialog`-based messaging (`showMessage`), and the RFID power dialog. Override `onConnectionStateChange`, `onStopScanning`, `onDestroyUHF`, `isScanning()`, and `ReaderOnKeyDwon()` (physical trigger button) in subclasses rather than duplicating connection plumbing.
- **Navigation**: `androidx.navigation` + Safe Args, single `nav_host_fragment_content_main` inside `MainActivity` (`res/navigation/nav_graph.xml`). `LoginActivity`/`SetupActivity`/`DeviceListActivity` sit outside the graph as separate Activities.
- Physical scan-trigger key codes (`139`, `280`, `293` — standard on UHF/BLE handhelds) are caught in `MainActivity.onKeyUp` and forwarded to `currentFragment?.ReaderOnKeyDwon()`; `MainActivity.currentFragment` is set by each fragment itself in `onViewCreated`.

## RFID SDK (`com.rscja.deviceapi`)

Comes from `app/libs/DeviceAPI_ver20230228_release.aar` (Chainway/RSCJA UHF-BLE SDK). Core pattern used throughout (see `PairingFragment`, `StockOpnameScanFragment`):

```kotlin
uhf?.startInventoryTag()          // begin continuous tag scan
// tags arrive as UHFTAGInfo via a Handler message loop, not a direct callback in the active code paths
uhf?.stopInventory()
```

Several fragments have an older `setInventoryCallback { uhftagInfo -> ... }` path **commented out** in favor of the Handler/message approach — when extending scanning behavior, follow the live (uncommented) pattern, not the commented one.

## Backend integration (`utils/rest`)

`ApiInterface` is a Retrofit interface whose endpoints map directly to `TritunggalWeb.Api` controllers: `api/Auth/SignIn`, `api/Rfid/*`, `api/Inv/*`, `api/Loc/*`, `api/Master/*`. `ApiClient.setup(context, baseUrl)` must be called before `ApiClient.client` is first used (it lazily builds the `Retrofit` instance and caches it — call `setup` again with a new URL to force a rebuild, since `retrofit` is only reset to `null`, not rebuilt, on `setup`).

- **Auth is not centralized.** `JwtInterceptor.kt` exists but is an empty file — it's dead code, not wired into `ApiClient`'s `OkHttpClient`. Every authenticated call instead passes the token manually as a header parameter at the call site: `apiInterface.xxx("Bearer " + sessionManager.getSessionId(), request)`. When adding a new authenticated endpoint, copy this per-call-site pattern rather than assuming an interceptor will attach it.
- `BaseResponse` (`result: Int`, `RESULT_OK = 0` / `RESULT_NOK = 1`) mirrors `TritunggalWeb`'s `BaseResponse.Result` convention exactly — check `response.body()?.result == BaseResponse.RESULT_OK`, never treat it as a bool.
- Requests/responses live in parallel `utils/rest/requests/` and `utils/rest/response/` packages, one class per endpoint, named to match (`GetItemByEpcRequest` ↔ `GetItemByEpcResponse`).

## Session & config (`SessionManager`)

Wraps a single `SharedPreferences("rfid")` store: `sessionId` (the bearer token from login), `serverUrl` (defaults to `Params.URL`, overridable per-device via `SetupActivity` — this is how different handhelds can point at different backend environments), `deviceAddress` (last-paired BT reader, for auto-reconnect), `power` (RFID radio power, `Params.MIN_POWER..MAX_POWER`), and `menu` (the server-returned, permission-scoped feature list from `SignIn`, matched against `Params.MENU_*` constants to show/hide cards on `HomeFragment` — this is the mobile equivalent of `AccessMenu` gating in the web CRM).

## Config & secrets — don't add more

- `Params.URL` is hardcoded to production: `https://tritunggal.sistemdigital.my.id:444/`, with a commented-out emulator alternative `http://10.0.2.2:5054/` (matches `TritunggalWeb.Api`'s local dev port).
- `Params.DEBUG = true` currently pre-fills `admin`/`Admin` into the login form — a dev convenience left on; flip to `false` before a real release build if that matters.
- Both debug and release `signingConfigs` in `app/build.gradle.kts` point at `app/uhf-serial_release.jks` with a hardcoded plaintext store/key password.
- A Sentry DSN is embedded directly in `AndroidManifest.xml` (`io.sentry.dsn`), with PII collection and screenshot/view-hierarchy attachment on crash all enabled.
- `android:usesCleartextTraffic="true"` is set — the app can talk plain HTTP (used for the emulator/local dev URL above).

Don't echo these values in output, and don't introduce new plaintext credentials elsewhere in the app.
