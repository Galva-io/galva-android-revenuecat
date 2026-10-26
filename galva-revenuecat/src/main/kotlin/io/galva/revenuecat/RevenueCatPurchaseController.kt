package io.galva.revenuecat

import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import io.galva.sdk.GalvaPurchaseController
import io.galva.sdk.GalvaPurchaseException
import io.galva.sdk.GalvaPurchaseRequest
import io.galva.sdk.GalvaPurchaseResult
import io.galva.sdk.IntegrationLogger
import kotlinx.coroutines.CancellationException

/**
 * Runs the purchases Galva in-app messages request through RevenueCat, so RevenueCat records the
 * purchase and refreshes `CustomerInfo` in the same session. Without it, Galva buys through Play
 * directly and RevenueCat only learns about the purchase the next time it queries Play.
 *
 * Routing
 * - RevenueCat completes purchases (`PurchasesAreCompletedBy.REVENUECAT`, the default) →
 *   `Purchases.purchase` with the matching base plan and offer. RevenueCat records and acknowledges
 *   it. Unlike iOS, Play offers need no Galva signature, so RevenueCat can apply every offer and
 *   there is no StoreKit-style fallback for offers.
 * - The app completes purchases (`MY_APP`) → Galva's Play flow, then `syncPurchases()` so
 *   RevenueCat records it, as RevenueCat requires for purchases made outside its API.
 * - RevenueCat not configured yet → Galva's Play flow; RevenueCat picks the purchase up from Play
 *   once it starts.
 *
 * A failed hand-off to RevenueCat after the user paid is logged, never reported as a failed
 * purchase.
 */
internal class RevenueCatPurchaseController(
    private val revenueCat: RevenueCatAdapter,
    private val logger: IntegrationLogger,
) : GalvaPurchaseController {

    override suspend fun purchase(request: GalvaPurchaseRequest): GalvaPurchaseResult = when {
        !revenueCat.isConfigured -> {
            logger.warn { "RevenueCat isn't configured; buying ${request.productId} with Galva's Play Billing flow" }
            request.purchaseWithPlayBilling()
        }

        revenueCat.appCompletesPurchases -> request.purchaseWithPlayBilling().also { result ->
            if (result is GalvaPurchaseResult.Completed) sync()
        }

        else -> purchaseWithRevenueCat(request)
    }

    private suspend fun purchaseWithRevenueCat(request: GalvaPurchaseRequest): GalvaPurchaseResult {
        val outcome = try {
            revenueCat.purchase(request.activity, request.productId, request.basePlanId, request.offerId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GalvaPurchaseException) {
            throw e
        } catch (e: Exception) {
            return result(e)
        }
        return when (outcome) {
            RevenueCatPurchaseOutcome.Cancelled -> GalvaPurchaseResult.Cancelled
            is RevenueCatPurchaseOutcome.Completed -> GalvaPurchaseResult.Completed(outcome.transaction)
        }
    }

    private suspend fun sync() {
        try {
            revenueCat.syncPurchases()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "RevenueCat syncPurchases failed; RevenueCat picks the purchase up from Play later" }
        }
    }

    companion object {
        /**
         * Maps an error thrown by RevenueCat's purchase API onto the outcome the in-app message
         * understands: cancellation and pending payments are normal outcomes, everything else
         * becomes a [GalvaPurchaseException].
         */
        fun result(error: Exception): GalvaPurchaseResult {
            if (error is PurchasesTransactionException && error.userCancelled) return GalvaPurchaseResult.Cancelled
            if (error !is PurchasesException) throw GalvaPurchaseException.Failed(error.message ?: error.toString())
            val detail = error.message
            return when (error.code) {
                PurchasesErrorCode.PurchaseCancelledError -> GalvaPurchaseResult.Cancelled
                PurchasesErrorCode.PaymentPendingError -> GalvaPurchaseResult.Pending
                PurchasesErrorCode.ProductNotAvailableForPurchaseError -> throw GalvaPurchaseException.ProductUnavailable(detail)
                PurchasesErrorCode.PurchaseNotAllowedError -> throw GalvaPurchaseException.PurchaseNotAllowed(detail)
                PurchasesErrorCode.IneligibleError -> throw GalvaPurchaseException.IneligibleForOffer(detail)
                // StoreProblemError covers Play being unreachable, like the built-in flow's network errors.
                PurchasesErrorCode.NetworkError,
                PurchasesErrorCode.StoreProblemError -> throw GalvaPurchaseException.NetworkError(detail)
                PurchasesErrorCode.SignatureVerificationError -> throw GalvaPurchaseException.VerificationFailed(detail)
                else -> throw GalvaPurchaseException.Failed(detail)
            }
        }
    }
}
