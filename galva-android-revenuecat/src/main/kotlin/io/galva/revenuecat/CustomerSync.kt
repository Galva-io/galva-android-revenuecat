package io.galva.revenuecat

import io.galva.sdk.IntegrationLogger
import kotlinx.coroutines.flow.first

/**
 * Applies RevenueCat customer updates to Galva, off the main thread.
 *
 * Waits until RevenueCat is configured (checking right away, then on each app foreground), so
 * `Purchases.configure` may run before or after `Galva.configure`. Updates are handled one at a
 * time, in order, on one coroutine; [SyncState] decides what each one means for Galva.
 */
internal class CustomerSync(
    followsRevenueCatIdentity: Boolean,
    private val galva: GalvaAdapter,
    private val logger: IntegrationLogger,
) {
    private val state = SyncState(followsRevenueCatIdentity)

    /**
     * Waits for RevenueCat, then applies its updates for the life of the app. Returns early only
     * if [foregrounds] ends before RevenueCat is configured.
     */
    suspend fun run(revenueCat: RevenueCatAdapter, foregrounds: ForegroundSignals) {
        if (!waitForConfiguration(revenueCat, foregrounds)) return

        logger.info {
            "connected to RevenueCat ${revenueCat.frameworkVersion} " +
                "(purchasesAreCompletedBy=${if (revenueCat.appCompletesPurchases) "MY_APP" else "REVENUECAT"}, " +
                "identity=${if (state.followsRevenueCatIdentity) "FOLLOW_REVENUECAT" else "MANUAL"})"
        }
        // A logOut made offline in an earlier launch left Galva on the previous user.
        state.seed(galva.identifiedUserId)
        // Foregrounds keep re-attaching to RevenueCat's listener, in case the app replaced it.
        revenueCat.customerUpdates(foregrounds.events).collect(::apply)
    }

    fun apply(customer: RevenueCatCustomer) {
        state.apply(customer).forEach(::perform)
    }

    private suspend fun waitForConfiguration(revenueCat: RevenueCatAdapter, foregrounds: ForegroundSignals): Boolean {
        if (revenueCat.isConfigured) return true
        logger.info {
            "RevenueCat isn't configured yet; will connect on a later app foreground. " +
                "Call Purchases.configure(...) in Application.onCreate()."
        }
        return try {
            foregrounds.events.first { revenueCat.isConfigured }
            true
        } catch (e: NoSuchElementException) {
            false
        }
    }

    private fun perform(action: SyncAction) {
        when (action) {
            is SyncAction.Identify -> {
                logger.info { "identifying Galva user from RevenueCat: ${action.userId}" }
                galva.identify(action.userId)
            }

            SyncAction.Logout -> {
                logger.info { "RevenueCat user changed; logging Galva out" }
                galva.logout()
            }

            is SyncAction.SetAttributes -> {
                logger.debug { "updating RevenueCat attributes (activeEntitlements=${action.attributes.activeEntitlements})" }
                galva.setAttributes(action.attributes)
            }

            SyncAction.ReconcileTransactions -> {
                logger.debug { "RevenueCat reported a new purchase; reporting it to Galva" }
                galva.reconcileTransactions()
            }
        }
    }
}
