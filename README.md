# Galva RevenueCat for Android

Use the [Galva Android SDK](https://github.com/Galva-io/galva-android) in an app that uses [RevenueCat](https://www.revenuecat.com):

- offers bought in Galva's in-app messages go through RevenueCat,
- Galva follows RevenueCat's user: `Purchases.logIn` / `logOut` also sign the Galva user in and out,
- Galva gets the RevenueCat app user ID and active entitlements.

**Requirements:** minSdk 24, RevenueCat `purchases` 10.x.

---

## 1. Install

The package is on [Maven Central](https://central.sonatype.com/artifact/io.galva.sdk/galva-android-revenuecat), like the Galva SDK, so it needs no extra repository or credentials. Check that your `settings.gradle.kts` lists `mavenCentral()`, as new Android projects do:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Add the dependency in `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.galva.sdk:galva-android-revenuecat:1.0.0")
}
```

It brings the Galva SDK and RevenueCat. If your app already declares RevenueCat 10.x, your version is used.

## 2. Configure

In your `Application`, configure RevenueCat, then Galva with the RevenueCat integration:

```kotlin
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import io.galva.revenuecat.revenueCat
import io.galva.sdk.Configuration
import io.galva.sdk.Environment
import io.galva.sdk.Galva
import io.galva.sdk.GalvaIntegration

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Purchases.configure(PurchasesConfiguration.Builder(this, "goog_your_revenuecat_key").build())
        Galva.configure(
            this,
            Configuration(
                apiKey = BuildConfig.GALVA_API_KEY,
                env = if (BuildConfig.DEBUG) Environment.Development else Environment.Production,
            ),
            integrations = listOf(GalvaIntegration.revenueCat()),
        )
    }
}
```

```java
Purchases.configure(new PurchasesConfiguration.Builder(this, "goog_your_revenuecat_key").build());
Galva.configure(this, new Configuration(BuildConfig.GALVA_API_KEY),
        Collections.singletonList(GalvaRevenueCat.integration()));
```

To check it, run a debug build with `logLevel = LogLevel.DEBUG` and filter Logcat on the `Galva` tag: you should see `[revenuecat] connected to RevenueCat`.

## 3. Sign users in and out with RevenueCat

Use RevenueCat's calls only; Galva follows them:

```kotlin
Purchases.sharedInstance.logIn("user_42") // the Galva user becomes user_42
Purchases.sharedInstance.logOut()         // Galva goes back to an anonymous user
```

Don't call `Galva.instance.identify` or `logout` yourself. If Galva must use different user IDs than RevenueCat, keep your own `identify` / `logout` calls and switch the integration to manual identity:

```kotlin
GalvaIntegration.revenueCat(identity = RevenueCatIdentity.MANUAL)
```

```java
GalvaRevenueCat.integration(RevenueCatIdentity.MANUAL)
```

## 4. In-app messages, deep links and push

Set them up as in the Galva SDK README: [in-app messages](https://github.com/Galva-io/galva-android#in-app-messages), [deep links](https://github.com/Galva-io/galva-android#deep-links) and [push notifications](https://github.com/Galva-io/galva-android#push-notifications). Offers bought inside a message go through RevenueCat with no extra code.

## 5. Connect RevenueCat in the Galva dashboard

Galva also needs RevenueCat's subscription events. In Galva, open **Admin → Settings → Connect → Billing Connections → RevenueCat**, then add a RevenueCat V2 secret API key and a RevenueCat webhook to Galva's URL. See [Connect RevenueCat](https://docs.galva.io/integrations/revenuecat).

---

**Good to know**

- Your own `UpdatedCustomerInfoListener` keeps receiving every update: the integration chains onto it.
- The integration runs message purchases itself, so don't call `Galva.instance.setPurchaseController`.
- Purchases RevenueCat reports, your own paywall's included, reach Galva right away: there is no `Galva.instance.reconcileTransactions()` to call.

Working on this package: see [CONTRIBUTING.md](CONTRIBUTING.md). License: MIT, see [LICENSE](LICENSE).
