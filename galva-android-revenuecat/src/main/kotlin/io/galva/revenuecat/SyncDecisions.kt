package io.galva.revenuecat

import io.galva.sdk.ProfileProperty

/**
 * What to do in Galva when RevenueCat's customer state changes, as plain logic with no side
 * effects. [CustomerSync] feeds it each RevenueCat update and carries out the actions it returns,
 * in order. Same rules as the iOS integration.
 *
 * Identity (when following RevenueCat)
 * - Identified as X, Galva last mirrored nobody → identify(X).
 * - Switched straight from X to Y (`logIn` without `logOut`) → logout, then identify(Y), so Y
 *   never inherits X's anonymous id or pending events.
 * - Identified → anonymous → logout.
 * - Anonymous the whole time → nothing; the app may identify Galva itself.
 *
 * The mirrored user starts as the user Galva is identified as when the sync connects ([SyncState.seed]).
 * RevenueCat reports no update for a `logOut` made offline, so the next launch may find RevenueCat
 * anonymous while Galva still has the previous user: that is a logout, not an anonymous launch,
 * and the next `logIn` must not identify a new user on the previous user's identity.
 *
 * Unlike iOS, the app user ID is not passed as a purchase token: RevenueCat attaches
 * `Base64(SHA-256(appUserID))` as Play's `obfuscatedAccountId`, a format Galva's own purchases
 * don't use, so Galva's backend links RevenueCat purchases through RevenueCat's events instead.
 *
 * Attributes
 * `$gv_rc_appUserId`, `$gv_rc_originalAppUserId` and `$gv_rc_activeEntitlements`, sent when they
 * change, and again after every identity change, since attributes belong to the Galva user.
 *
 * Transactions
 * A product purchased, or purchased again (a renewal), since the previous update →
 * reconcileTransactions, so Galva links the purchase now rather than on the next app entry.
 */
internal sealed class SyncAction {
    data class Identify(val userId: String) : SyncAction()
    data object Logout : SyncAction()
    data class SetAttributes(val attributes: RevenueCatAttributes) : SyncAction()
    data object ReconcileTransactions : SyncAction()
}

/** RevenueCat customer state, captured on every `CustomerInfo` update. */
internal data class RevenueCatCustomer(
    val appUserId: String,
    val isAnonymous: Boolean,
    val originalAppUserId: String,
    /** Identifiers of the active entitlements, sorted. */
    val activeEntitlements: List<String>,
    /** Latest purchase time (epoch millis) of each product the customer bought. */
    val latestPurchaseTimes: Map<String, Long> = emptyMap(),
)

/** The RevenueCat state Galva keeps as user attributes. */
internal data class RevenueCatAttributes(
    val appUserId: String,
    val originalAppUserId: String,
    val activeEntitlements: List<String>,
) {
    constructor(customer: RevenueCatCustomer) :
        this(customer.appUserId, customer.originalAppUserId, customer.activeEntitlements)

    fun toProperties(): List<ProfileProperty> = listOf(
        ProfileProperty.Custom(APP_USER_ID_KEY, appUserId),
        ProfileProperty.Custom(ORIGINAL_APP_USER_ID_KEY, originalAppUserId),
        ProfileProperty.Custom(ACTIVE_ENTITLEMENTS_KEY, activeEntitlements),
    )

    companion object {
        const val APP_USER_ID_KEY = "\$gv_rc_appUserId"
        const val ORIGINAL_APP_USER_ID_KEY = "\$gv_rc_originalAppUserId"
        const val ACTIVE_ENTITLEMENTS_KEY = "\$gv_rc_activeEntitlements"
    }
}

/** What the sync remembers between RevenueCat updates. Not thread-safe: used from one coroutine. */
internal class SyncState(val followsRevenueCatIdentity: Boolean) {

    /** RevenueCat app user ID last mirrored into Galva (or seeded from it); null while anonymous or managed by the app. */
    var mirroredUserId: String? = null
        private set

    /** Attributes last sent for the current Galva user. */
    var sentAttributes: RevenueCatAttributes? = null
        private set

    /** Purchase times of the previous update; null before the first one. */
    private var seenPurchaseTimes: Map<String, Long>? = null

    /**
     * Starts from the user Galva is identified as ([galvaUserId], null while anonymous), so a change
     * RevenueCat made while the sync wasn't watching is still mirrored. Call before the first [apply].
     */
    fun seed(galvaUserId: String?) {
        if (followsRevenueCatIdentity) mirroredUserId = galvaUserId
    }

    /** Folds one RevenueCat update into the state; returns the Galva calls to make, in order. */
    fun apply(customer: RevenueCatCustomer): List<SyncAction> {
        val actions = if (followsRevenueCatIdentity) identityActions(customer) else mutableListOf()

        val attributes = RevenueCatAttributes(customer)
        if (attributes != sentAttributes) {
            actions += SyncAction.SetAttributes(attributes)
            sentAttributes = attributes
        }
        // After the identity actions: the purchase is reported for the user they leave in place.
        if (hasNewPurchase(customer.latestPurchaseTimes)) actions += SyncAction.ReconcileTransactions
        seenPurchaseTimes = customer.latestPurchaseTimes
        return actions
    }

    /** A product bought, or bought again, since the previous update. The first update is the baseline. */
    private fun hasNewPurchase(purchaseTimes: Map<String, Long>): Boolean {
        val seen = seenPurchaseTimes ?: return false
        return purchaseTimes.any { (product, time) -> time > (seen[product] ?: Long.MIN_VALUE) }
    }

    private fun identityActions(customer: RevenueCatCustomer): MutableList<SyncAction> {
        if (customer.isAnonymous) {
            if (mirroredUserId == null) return mutableListOf()
            mirroredUserId = null
            sentAttributes = null
            return mutableListOf(SyncAction.Logout)
        }
        if (customer.appUserId == mirroredUserId) return mutableListOf()

        val actions: MutableList<SyncAction> = if (mirroredUserId == null) mutableListOf() else mutableListOf(SyncAction.Logout)
        actions += SyncAction.Identify(customer.appUserId)
        mirroredUserId = customer.appUserId
        sentAttributes = null
        return actions
    }
}
