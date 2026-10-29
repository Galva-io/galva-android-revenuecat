package io.galva.revenuecat

import android.app.Activity
import com.revenuecat.purchases.CacheFetchPolicy
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesAreCompletedBy
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitGetProducts
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitSyncPurchases
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.GoogleStoreProduct
import com.revenuecat.purchases.models.GoogleSubscriptionOption
import com.revenuecat.purchases.models.StoreTransaction
import io.galva.sdk.GalvaPurchaseException
import io.galva.sdk.GalvaTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How a purchase made with RevenueCat's API ended. Pending and failures arrive as thrown `PurchasesException`s. */
internal sealed class RevenueCatPurchaseOutcome {
    data class Completed(val transaction: GalvaTransaction) : RevenueCatPurchaseOutcome()
    data object Cancelled : RevenueCatPurchaseOutcome()
}

/**
 * The slice of RevenueCat this package uses, behind an interface so purchase routing and the
 * customer sync are testable without a configured `Purchases`.
 */
internal interface RevenueCatAdapter {
    /** False until `Purchases.configure(...)` runs. Everything else touches `Purchases.sharedInstance`, which throws before that. */
    val isConfigured: Boolean

    /** True when the app completes purchases itself (`PurchasesAreCompletedBy.MY_APP`). */
    val appCompletesPurchases: Boolean
    val frameworkVersion: String

    /**
     * Buys [basePlanId] of subscription [productId], with [offerId] applied when set.
     * @throws GalvaPurchaseException when RevenueCat doesn't offer that base plan or offer.
     * @throws com.revenuecat.purchases.PurchasesException when RevenueCat's purchase fails.
     */
    suspend fun purchase(activity: Activity, productId: String, basePlanId: String, offerId: String?): RevenueCatPurchaseOutcome
    suspend fun syncPurchases()

    /** RevenueCat's current customer state first, then every change. [refresh] re-reads it. */
    fun customerUpdates(refresh: Flow<Unit>): Flow<RevenueCatCustomer>
}

internal class LiveRevenueCatAdapter : RevenueCatAdapter {

    override val isConfigured: Boolean get() = Purchases.isConfigured

    override val appCompletesPurchases: Boolean
        get() = Purchases.sharedInstance.purchasesAreCompletedBy == PurchasesAreCompletedBy.MY_APP

    override val frameworkVersion: String get() = Purchases.frameworkVersion

    override suspend fun purchase(
        activity: Activity,
        productId: String,
        basePlanId: String,
        offerId: String?,
    ): RevenueCatPurchaseOutcome {
        val purchases = Purchases.sharedInstance
        // A subscription comes back as one StoreProduct per base plan.
        val product = purchases.awaitGetProducts(listOf(productId), ProductType.SUBS)
            .filterIsInstance<GoogleStoreProduct>()
            .firstOrNull { it.productId == productId && it.basePlanId == basePlanId }
            ?: throw GalvaPurchaseException.ProductUnavailable("RevenueCat has no base plan $basePlanId of $productId")
        // Play only lists the offers the user is eligible for.
        val option = product.subscriptionOptions
            ?.filterIsInstance<GoogleSubscriptionOption>()
            ?.firstOrNull { it.basePlanId == basePlanId && it.offerId == offerId }
            ?: throw if (offerId == null) {
                GalvaPurchaseException.ProductUnavailable("RevenueCat has no base plan option for $productId:$basePlanId")
            } else {
                GalvaPurchaseException.IneligibleForOffer("Offer $offerId of $productId:$basePlanId is not available to this user")
            }
        val result = purchases.awaitPurchase(PurchaseParams.Builder(activity, option).build())
        return RevenueCatPurchaseOutcome.Completed(result.storeTransaction.toGalvaTransaction(productId))
    }

    override suspend fun syncPurchases() {
        Purchases.sharedInstance.awaitSyncPurchases()
    }

    /**
     * RevenueCat has a single `updatedCustomerInfoListener` so the app's own listener still receives every update, and
     * re-attaches on [refresh] (app foreground) if the app set a new listener since.
     */
    override fun customerUpdates(refresh: Flow<Unit>): Flow<RevenueCatCustomer> = callbackFlow {
        val purchases = Purchases.sharedInstance
        val listener = ChainedCustomerInfoListener { info -> trySend(info.toCustomer(purchases)) }

        // RevenueCat calls listeners on the main thread; attach there as well.
        suspend fun attach() = withContext(Dispatchers.Main) {
            val current = purchases.updatedCustomerInfoListener
            if (current !== listener) {
                listener.previous = current
                purchases.updatedCustomerInfoListener = listener
            }
        }

        // Offline on first launch there is nothing to read yet; the listener reports it later.
        suspend fun sendCurrent() {
            try {
                trySend(purchases.awaitCustomerInfo(CacheFetchPolicy.CACHED_OR_FETCHED).toCustomer(purchases))
            } catch (e: PurchasesException) {
                // Nothing to send.
            }
        }

        attach()
        // A newly attached listener only hears about changes, so read the current state.
        sendCurrent()
        launch {
            refresh.collect {
                attach()
                sendCurrent()
            }
        }
        awaitClose {
            if (purchases.updatedCustomerInfoListener === listener) {
                purchases.updatedCustomerInfoListener = listener.previous
            }
        }
    }

    private class ChainedCustomerInfoListener(
        private val onUpdate: (CustomerInfo) -> Unit,
    ) : UpdatedCustomerInfoListener {
        @Volatile
        var previous: UpdatedCustomerInfoListener? = null

        override fun onReceived(customerInfo: CustomerInfo) {
            previous?.onReceived(customerInfo)
            onUpdate(customerInfo)
        }
    }

    private companion object {
        fun CustomerInfo.toCustomer(purchases: Purchases) = RevenueCatCustomer(
            appUserId = purchases.appUserID,
            isAnonymous = purchases.isAnonymous,
            originalAppUserId = originalAppUserId,
            activeEntitlements = entitlements.active.keys.sorted(),
            latestPurchaseTimes = allPurchaseDatesByProduct.entries
                .mapNotNull { (product, date) -> date?.let { product to it.time } }
                .toMap(),
        )

        fun StoreTransaction.toGalvaTransaction(productId: String) = GalvaTransaction(
            orderId = orderId,
            productId = productIds.firstOrNull() ?: productId,
            purchaseToken = purchaseToken,
            purchaseTime = purchaseTime,
            // Play's purchase JSON carries the account id RevenueCat attached.
            obfuscatedAccountId = originalJson.optString("obfuscatedAccountId").takeIf { it.isNotEmpty() },
        )
    }
}
