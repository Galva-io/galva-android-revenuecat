# Galva RevenueCat for Android

Connect the [Galva Android SDK](https://github.com/Galva-io/galva-android) to [RevenueCat](https://www.revenuecat.com) with one line:

```kotlin
Galva.configure(this, Configuration(apiKey = "pk_your_app_id"), integrations = listOf(GalvaIntegration.revenueCat()))
```

- **Offers bought in Galva's messages go through RevenueCat.** RevenueCat records and acknowledges the purchase, and the subscriber's entitlement unlocks right away. Without this, RevenueCat only learns about those purchases the next time it queries Google Play.
- **Galva follows RevenueCat's identity.** `Purchases.logIn` and `logOut` identify and log out the Galva user too, so you don't call `Galva.instance.identify` yourself.
- **Galva knows the RevenueCat customer.** The app user ID and active entitlements are kept as Galva attributes.

> **Status: pre-release.** `settings.gradle.kts` builds `galva-sdk` from a sibling `../galva-android` checkout until galva-android ships the integrations API in a published release.

---

## Quick start

These steps assume you've registered your app in Galva and copied its App ID. See the [Galva Android installation guide](https://github.com/Galva-io/galva-android/blob/main/docs/installation.md).

### 1. Add the dependency

Add Galva's GitHub Packages repository as described in the installation guide, then:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.galva.sdk:galva-revenuecat:<version>")
}
```

It brings in `galva-sdk` and RevenueCat's `purchases` (with its Google Play Billing Library). If your app already depends on RevenueCat, keep your version: Gradle uses the newer of the two.

### 2. Configure

Configure RevenueCat, then pass `GalvaIntegration.revenueCat()` to Galva:

```kotlin
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import io.galva.core.protocol.Configuration
import io.galva.revenuecat.revenueCat
import io.galva.sdk.Galva
import io.galva.sdk.GalvaIntegration

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Purchases.configure(PurchasesConfiguration.Builder(this, "goog_your_key").build())
        Galva.configure(
            this,
            Configuration(apiKey = "pk_your_app_id", autoDisplayInAppMessages = true),
            integrations = listOf(GalvaIntegration.revenueCat()),
        )
    }
}
```

From Java: `Galva.configure(this, configuration, List.of(GalvaRevenueCat.integration()))`.

From now on, use RevenueCat as usual. `Purchases.sharedInstance.logIn("user_42")` identifies the Galva user as `user_42`. If you followed Galva's docs and call `Galva.instance.identify(Purchases.sharedInstance.appUserID)`, you can remove that call.

The order of the two `configure` calls doesn't matter. If RevenueCat is configured later, for example after an async step, the integration connects the next time your app comes to the foreground. Until then, offers bought in Galva's messages use Galva's own Play Billing purchase.

### 3. Connect RevenueCat to Galva

The app integration covers your app. Galva also needs RevenueCat's subscription events, from its webhook:

1. In Galva, open **Admin → Settings → Connect → Billing Connections** and select **RevenueCat**.
2. In RevenueCat, create a V2 secret API key and add it to Galva.
3. In RevenueCat, add a webhook with Galva's webhook URL and authorization header, with every event type on.

See [Connect RevenueCat](https://docs.galva.io/integrations/revenuecat) for each step. The RevenueCat connection is in development.

### 4. Check it works

Run your app with `logLevel = LogLevel.DEBUG` in the `Configuration` and filter Logcat on the `Galva` tag. You should see `[revenuecat] connected to RevenueCat`, with the RevenueCat version and mode. After `Purchases.sharedInstance.logIn`, you should see `[revenuecat] identifying Galva user from RevenueCat`.

---

## If Galva uses different user IDs

By default, Galva's user follows RevenueCat's app user ID. If you identify Galva with a different ID, keep your own `identify` and `logout` calls and tell the integration:

```kotlin
integrations = listOf(GalvaIntegration.revenueCat(identity = RevenueCatIdentity.MANUAL))
```

Purchases and attributes still sync.

## What the integration does

### Identity

| In RevenueCat | In Galva |
|---|---|
| Logged in as `X` | `Galva.instance.identify("X")` |
| Switched from `X` to `Y` without logging out | `Galva.instance.logout()`, then `identify("Y")` |
| Logged out, back to anonymous | `Galva.instance.logout()` |
| Anonymous the whole time | Nothing. Galva may be identified separately. |

### Purchases from Galva's messages

| RevenueCat `purchasesAreCompletedBy` | What runs |
|---|---|
| `REVENUECAT` (default) | `Purchases.purchase` with the message's base plan and offer. RevenueCat records and acknowledges the purchase. |
| `MY_APP` | Galva's Play Billing purchase, then `syncPurchases()` so RevenueCat records it. |
| RevenueCat not configured yet | Galva's Play Billing purchase. RevenueCat picks it up from Play once it starts. |

An offer the user isn't eligible for is reported to the message as ineligible, as with Galva's own flow. If handing a purchase to RevenueCat fails after the user paid, the user doesn't see an error; RevenueCat picks the purchase up from Play later.

### Attributes

Each Galva user gets these attributes, updated when they change:

| Attribute | Value |
|---|---|
| `$gv_rc_appUserId` | RevenueCat app user ID, including anonymous `$RCAnonymousID:…` IDs |
| `$gv_rc_originalAppUserId` | RevenueCat's original app user ID |
| `$gv_rc_activeEntitlements` | Identifiers of the active entitlements |

### Effect on your app

- **The sync stays off the main thread.** It runs on Galva's background coroutines, and RevenueCat does its work in the background. The only main-thread work is routing a purchase someone starts in a Galva message, and attaching the listener below.
- **Your `UpdatedCustomerInfoListener` keeps working.** RevenueCat on Android has a single customer listener, so the integration chains onto it: your listener still receives every update. If you set a listener after Galva connects, the integration re-attaches on the next app foreground.
- **It installs Galva's purchase controller.** Don't call `Galva.instance.setPurchaseController` yourself as well.

Its log lines go through Galva's logger with a `[revenuecat]` prefix, and follow your log level and custom `LogSink`.

## Differences from the iOS integration

- **No shared purchase token.** On iOS, a UUID app user ID becomes the StoreKit app account token both SDKs attach. On Android, RevenueCat attaches `Base64(SHA-256(appUserID))` as Play's `obfuscatedAccountId`, a format Galva's own purchases don't use, so Galva links RevenueCat purchases through RevenueCat's webhook events and the `$gv_rc_appUserId` attribute.
- **Every offer goes through RevenueCat.** Play offers need no Galva signature, so RevenueCat can apply all of them. iOS falls back to StoreKit for Galva-signed promotional offers.
- **No transaction reconciliation call.** Galva's backend links Play purchases to users itself.

## Requirements

| | |
|---|---|
| **Min SDK** | 24 (Android 7.0), as for Galva |
| **RevenueCat** | `com.revenuecat.purchases:purchases` 10.0.0 or later (10.x): the first release on Play Billing 8, which Galva requires |
| **Galva** | `io.galva.sdk:galva-sdk` with the integrations API |

## Development

```bash
./gradlew :galva-revenuecat:testDebugUnitTest
```

The build expects a `galva-android` checkout next to this one (see `settings.gradle.kts`) and the same Android Gradle Plugin and Kotlin versions, since the composite build loads both builds' plugins together.

## License

MIT. See [LICENSE](LICENSE).
