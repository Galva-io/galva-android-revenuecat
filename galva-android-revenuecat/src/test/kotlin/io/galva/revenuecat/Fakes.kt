package io.galva.revenuecat

import android.app.Activity
import io.galva.sdk.GalvaPurchaseController
import io.galva.sdk.GalvaPurchaseRequest
import io.galva.sdk.GalvaPurchaseResult
import io.galva.sdk.IntegrationLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.CopyOnWriteArrayList

internal class RecordingGalva : GalvaAdapter {
    val calls = CopyOnWriteArrayList<String>()
    var controller: GalvaPurchaseController? = null

    override fun identify(userId: String) {
        calls += "identify($userId)"
    }

    override fun logout() {
        calls += "logout"
    }

    override fun setAttributes(attributes: RevenueCatAttributes) {
        calls += "attributes(${attributes.appUserId},${attributes.originalAppUserId},${attributes.activeEntitlements})"
    }

    override fun setPurchaseController(controller: GalvaPurchaseController) {
        this.controller = controller
        calls += "setPurchaseController"
    }
}

internal class FakeRevenueCat(
    override var isConfigured: Boolean = true,
    override var appCompletesPurchases: Boolean = false,
) : RevenueCatAdapter {
    override val frameworkVersion = "10.0.0"
    val purchases = CopyOnWriteArrayList<String>()
    var purchaseResult: suspend () -> RevenueCatPurchaseOutcome = { RevenueCatPurchaseOutcome.Cancelled }
    var syncCount = 0
    var syncError: Exception? = null
    val customers = MutableSharedFlow<RevenueCatCustomer>(replay = 16)

    override suspend fun purchase(
        activity: Activity,
        productId: String,
        basePlanId: String,
        offerId: String?,
    ): RevenueCatPurchaseOutcome {
        purchases += listOfNotNull(productId, basePlanId, offerId).joinToString(":")
        return purchaseResult()
    }

    override suspend fun syncPurchases() {
        syncCount++
        syncError?.let { throw it }
    }

    override fun customerUpdates(refresh: Flow<Unit>): Flow<RevenueCatCustomer> = customers
}

internal object SilentLogger : IntegrationLogger {
    override fun debug(message: () -> String) = Unit
    override fun info(message: () -> String) = Unit
    override fun warn(throwable: Throwable?, message: () -> String) = Unit
    override fun error(throwable: Throwable?, message: () -> String) = Unit
}

internal fun customer(
    appUserId: String,
    isAnonymous: Boolean = false,
    originalAppUserId: String = appUserId,
    entitlements: List<String> = emptyList(),
) = RevenueCatCustomer(appUserId, isAnonymous, originalAppUserId, entitlements)

/** A message's purchase request whose built-in Play flow returns [playBilling] and counts its runs. */
internal class PlayBillingStub(private val playBilling: suspend () -> GalvaPurchaseResult) {
    var runs = 0

    fun request(
        activity: Activity,
        productId: String = "pro",
        basePlanId: String = "monthly",
        offerId: String? = "intro",
    ) = GalvaPurchaseRequest(activity, productId, basePlanId, offerId, "acct", "msg-1") {
        runs++
        playBilling()
    }
}
