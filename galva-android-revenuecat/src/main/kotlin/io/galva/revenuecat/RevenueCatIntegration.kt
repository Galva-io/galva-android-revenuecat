package io.galva.revenuecat

import io.galva.sdk.GalvaIntegration
import io.galva.sdk.IntegrationLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * The public surface: the `GalvaIntegration.revenueCat()` integration.
 *
 * ```kotlin
 * Purchases.configure(PurchasesConfiguration.Builder(this, "goog_…").build())
 * Galva.configure(this, Configuration(apiKey = "pk_…"), integrations = listOf(GalvaIntegration.revenueCat()))
 * ```
 *
 * Galva installs it once it's set up. From then on:
 * - purchases started inside Galva in-app messages run through RevenueCat
 *   ([RevenueCatPurchaseController]), so entitlements unlock in the same session;
 * - RevenueCat's identity and subscription state are mirrored into Galva off the main thread
 *   ([CustomerSync]).
 *
 * `Purchases.configure` may run before or after `Galva.configure`. Until RevenueCat is configured,
 * purchases use Galva's built-in Play Billing flow and the sync waits, re-checking on each app
 * foreground.
 */

/** How Galva's user relates to RevenueCat's. */
public enum class RevenueCatIdentity {
    /**
     * Galva's user follows RevenueCat's app user ID: `Purchases.logIn` identifies the Galva user
     * with the same ID and `Purchases.logOut` logs Galva out. You don't call
     * `Galva.instance.identify` or `logout` yourself.
     */
    FOLLOW_REVENUECAT,

    /**
     * You identify the Galva user yourself, e.g. when Galva uses a different user ID than
     * RevenueCat. Purchases and attributes still sync.
     */
    MANUAL,
}

/**
 * Connects Galva to RevenueCat. Pass it to
 * `Galva.configure(context, configuration, integrations = listOf(GalvaIntegration.revenueCat()))`.
 *
 * @param identity whether Galva's user follows RevenueCat's app user ID.
 */
public fun GalvaIntegration.Companion.revenueCat(
    identity: RevenueCatIdentity = RevenueCatIdentity.FOLLOW_REVENUECAT,
): GalvaIntegration = GalvaRevenueCat.integration(identity)

/** Entry point for Java callers: `GalvaRevenueCat.integration()`. */
public object GalvaRevenueCat {
    /** This package's version, reported to Galva with the integration. */
    public const val VERSION: String = BuildConfig.VERSION

    /** Same as `GalvaIntegration.revenueCat(identity)`. */
    @JvmStatic
    @JvmOverloads
    public fun integration(identity: RevenueCatIdentity = RevenueCatIdentity.FOLLOW_REVENUECAT): GalvaIntegration =
        GalvaIntegration(RevenueCatIntegration.NAME, VERSION) { context ->
            RevenueCatIntegration.install(
                identity = identity,
                logger = context.logger,
                revenueCat = LiveRevenueCatAdapter(),
                galva = LiveGalvaAdapter(),
                foregrounds = ForegroundSignals.app(),
                scope = context.scope,
            )
        }
}

internal object RevenueCatIntegration {
    const val NAME = "revenuecat"

    /**
     * Installs the purchase controller right away (it falls back to Galva's Play Billing flow
     * until RevenueCat is configured) and starts the sync.
     */
    fun install(
        identity: RevenueCatIdentity,
        logger: IntegrationLogger,
        revenueCat: RevenueCatAdapter,
        galva: GalvaAdapter,
        foregrounds: ForegroundSignals,
        scope: CoroutineScope,
    ) {
        galva.setPurchaseController(RevenueCatPurchaseController(revenueCat, logger))
        val sync = CustomerSync(
            followsRevenueCatIdentity = identity == RevenueCatIdentity.FOLLOW_REVENUECAT,
            galva = galva,
            logger = logger,
        )
        // Runs for the life of the app.
        scope.launch { sync.run(revenueCat, foregrounds) }
    }
}
