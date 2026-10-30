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

`galva-sdk` comes from Maven Central, or from Central's snapshot repository when the pinned version is a `-SNAPSHOT`. No credentials are needed.

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

1. Set `VERSION_NAME` in `gradle.properties` (`-SNAPSHOT` for pre-releases).
2. Merge to `main`. The `Publish to Maven Central` workflow runs the unit tests, then signs and publishes the package: a `-SNAPSHOT` version to Central's snapshot repository, any other version as a release, which the job waits on until it is published. The workflow can also be started by hand from the Actions tab.

- A release on Maven Central can't be deleted or replaced: bump `VERSION_NAME` before merging the next one.
- Release only once the pinned `galva-sdk` is released on Maven Central; apps can't resolve the package otherwise.
- Publishing a `-SNAPSHOT` needs snapshots enabled for the `io.galva.sdk` namespace in the Central Portal (Namespaces → Enable SNAPSHOTs).

The workflow needs the same repository secrets as galva-android's:

| Secret | Value |
|---|---|
| `SONATYPE_USERNAME`, `SONATYPE_PASSWORD` | A Central Portal user token (Account → Generate User Token), not the portal login |
| `SIGNING_KEY` | The ASCII-armored GPG private key that signs the artifacts |
| `SIGNING_KEY_ID` | Its key ID |
| `SIGNING_KEY_PASSWORD` | Its passphrase |

To try a build in an app first, publish it to `~/.m2` with `./gradlew :galva-android-revenuecat:publishToMavenLocal` (add `-Pgalva.mavenLocal` to build against a local galva-sdk). It needs no signing key; only Maven Central does.

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

At launch the sync starts from the user Galva is identified as. RevenueCat reports nothing for a `logOut` made offline, so the next launch can find RevenueCat anonymous while Galva still has the previous user: that is applied as a logout, so the next `logIn` gets a fresh Galva identity instead of the previous user's anonymous id and obfuscated account id.

**Purchases from Galva's messages**

| RevenueCat `purchasesAreCompletedBy` | What runs |
|---|---|
| `REVENUECAT` (default) | `Purchases.purchase` with the message's base plan and offer |
| `MY_APP` | Galva's Play Billing purchase, then `syncPurchases()` |
| RevenueCat not configured yet | Galva's Play Billing purchase |

**Transactions:** a product bought, or bought again (a renewal), since the previous `CustomerInfo` update calls `Galva.instance.reconcileTransactions()`, after any identity change. The first update after launch is the baseline; Galva's own sweep covers app entry.

**Attributes** kept on the Galva user: `$gv_rc_appUserId`, `$gv_rc_originalAppUserId`, `$gv_rc_activeEntitlements`.

**Differences from iOS:** no shared purchase token (RevenueCat attaches `Base64(SHA-256(appUserID))` as Play's `obfuscatedAccountId`, so Galva links purchases through RevenueCat's webhook, `$gv_rc_appUserId` and `reconcileTransactions()`); every offer goes through RevenueCat.

The sync runs on Galva's background coroutines; its log lines carry a `[revenuecat]` prefix.
