# Contributing to Galva RevenueCat for Android

How the package is built, tested and published. App developers only need the [README](README.md).

## Layout

```
:galva-android-revenuecat  the published package (io.galva.sdk:galva-android-revenuecat, Kotlin package io.galva.revenuecat)
:app                       demo app: RevenueCat + Galva wired together
```

## Build and test

```bash
./gradlew :galva-android-revenuecat:testDebugUnitTest
./gradlew :app:assembleDebug
```

`galva-sdk` comes from Galva's GitHub Packages repository: set `gpr.user` and `gpr.key` in `~/.gradle/gradle.properties`, or `GPR_USER` and `GPR_KEY` in the environment.

To build against a galva-android change that isn't published yet, publish it locally and turn Maven Local on, with `-Pgalva.mavenLocal` or, so Android Studio's sync sees it too, `galva.mavenLocal=true` in the git-ignored `local.properties`:

```bash
# in the galva-android checkout
./gradlew publishToMavenLocal
```

The `galva` version in `gradle/libs.versions.toml` must match galva-android's `VERSION_NAME`. Without the flag or the property, the build never reads Maven Local.

## Versions

- `galva` is pinned to the exact `galva-sdk` release the integration is built with.
- `revenuecat` is `strictly [10.0.0, 11.0.0)`: an app's own 10.x is used as is, an older one is raised to the latest 10.x (10.0.0 is the first on Play Billing 8, which Galva requires), and 11+ fails the app's build unless it declares RevenueCat strictly itself.
- `VERSION_NAME` in `gradle.properties` is also the integration version reported to Galva; bump it on every release.

## Publish

```bash
# This repository's GitHub Packages; needs gpr.user / gpr.token with write:packages
./gradlew :galva-android-revenuecat:publishReleasePublicationToGitHubPackagesRepository

# ~/.m2
./gradlew :galva-android-revenuecat:publishToMavenLocal
```

The pinned `galva-sdk` must be on GitHub Packages before apps can resolve a published build.

## Demo app

The `app` module is a control panel: use RevenueCat as an app would and watch Galva follow (RevenueCat logIn / logOut, offerings and purchases, Galva messages, a sample `gvrcdemo://` link). It sets its own `UpdatedCustomerInfoListener` before Galva connects, to show the integration chaining onto it. Add your keys to `local.properties`, then install:

```properties
GALVA_API_KEY=pk_your_app_id
REVENUECAT_API_KEY=goog_your_revenuecat_key
```

```bash
./gradlew :app:installDebug
```

A missing key doesn't fail the build; the status panel shows which SDK isn't configured. Real purchases need the app (`io.galva.revenuecat.demo`) in a Play Console track, a license tester on the device and the products in RevenueCat.

## Behaviour reference

**Identity** (`RevenueCatIdentity.FOLLOW_REVENUECAT`, the default)

| In RevenueCat | In Galva |
|---|---|
| Logged in as `X` | `identify("X")` |
| Switched from `X` to `Y` | `logout()`, then `identify("Y")` |
| Logged out, back to anonymous | `logout()` |
| Anonymous the whole time | Nothing |

**Purchases from Galva's messages**

| RevenueCat `purchasesAreCompletedBy` | What runs |
|---|---|
| `REVENUECAT` (default) | `Purchases.purchase` with the message's base plan and offer |
| `MY_APP` | Galva's Play Billing purchase, then `syncPurchases()` |
| RevenueCat not configured yet | Galva's Play Billing purchase |

**Attributes** kept on the Galva user: `$gv_rc_appUserId`, `$gv_rc_originalAppUserId`, `$gv_rc_activeEntitlements`.

**Differences from iOS:** no shared purchase token (RevenueCat attaches `Base64(SHA-256(appUserID))` as Play's `obfuscatedAccountId`, so Galva links purchases through RevenueCat's webhook and `$gv_rc_appUserId`); every offer goes through RevenueCat; no transaction reconciliation call.

The sync runs on Galva's background coroutines; its log lines carry a `[revenuecat]` prefix.
