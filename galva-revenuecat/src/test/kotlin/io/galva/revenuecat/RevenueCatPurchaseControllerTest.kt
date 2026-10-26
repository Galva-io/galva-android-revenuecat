package io.galva.revenuecat

import android.app.Activity
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import io.galva.sdk.GalvaPurchaseException
import io.galva.sdk.GalvaPurchaseResult
import io.galva.sdk.GalvaTransaction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import kotlin.reflect.KClass

@RunWith(RobolectricTestRunner::class)
class RevenueCatPurchaseControllerTest {
    private val activity = mock<Activity>()
    private val transaction = GalvaTransaction("GPA.1", "pro", "token", 0L)
    private val playTransaction = GalvaTransaction("GPA.play", "pro", "play-token", 0L, "acct")
    private val play = PlayBillingStub { GalvaPurchaseResult.Completed(playTransaction) }
    private val revenueCat = FakeRevenueCat()
    private val controller = RevenueCatPurchaseController(revenueCat, SilentLogger)

    private fun purchase(offerId: String? = "intro") = runBlocking { controller.purchase(play.request(activity, offerId = offerId)) }

    private fun failureOf(code: PurchasesErrorCode): KClass<*>? =
        runCatching { RevenueCatPurchaseController.result(PurchasesException(PurchasesError(code))) }
            .exceptionOrNull()?.let { it::class }

    @Test
    fun `RevenueCat buys the requested base plan and offer`() {
        revenueCat.purchaseResult = { RevenueCatPurchaseOutcome.Completed(transaction) }

        assertEquals(GalvaPurchaseResult.Completed(transaction), purchase())
        assertEquals(listOf("pro:monthly:intro"), revenueCat.purchases)
        assertEquals(0, play.runs)
    }

    @Test
    fun `a base plan without an offer goes through RevenueCat too`() {
        revenueCat.purchaseResult = { RevenueCatPurchaseOutcome.Completed(transaction) }

        purchase(offerId = null)

        assertEquals(listOf("pro:monthly"), revenueCat.purchases)
    }

    @Test
    fun `without RevenueCat configured Galva's Play flow runs`() {
        revenueCat.isConfigured = false

        assertEquals(GalvaPurchaseResult.Completed(playTransaction), purchase())
        assertEquals(1, play.runs)
        assertTrue(revenueCat.purchases.isEmpty())
        assertEquals(0, revenueCat.syncCount)
    }

    @Test
    fun `when the app completes purchases Galva's Play flow runs and RevenueCat syncs it`() {
        revenueCat.appCompletesPurchases = true

        assertEquals(GalvaPurchaseResult.Completed(playTransaction), purchase())
        assertEquals(1, play.runs)
        assertEquals(1, revenueCat.syncCount)
        assertTrue(revenueCat.purchases.isEmpty())
    }

    @Test
    fun `a failed sync after the user paid is still a completed purchase`() {
        revenueCat.appCompletesPurchases = true
        revenueCat.syncError = PurchasesException(PurchasesError(PurchasesErrorCode.NetworkError))

        assertEquals(GalvaPurchaseResult.Completed(playTransaction), purchase())
    }

    @Test
    fun `nothing is synced when the user cancels the Play flow`() {
        revenueCat.appCompletesPurchases = true
        val cancelled = PlayBillingStub { GalvaPurchaseResult.Cancelled }

        val result = runBlocking { controller.purchase(cancelled.request(activity)) }

        assertEquals(GalvaPurchaseResult.Cancelled, result)
        assertEquals(0, revenueCat.syncCount)
    }

    @Test
    fun `cancelling RevenueCat's purchase screen is a cancellation, not a failure`() {
        revenueCat.purchaseResult = {
            throw PurchasesTransactionException(PurchasesError(PurchasesErrorCode.PurchaseCancelledError), userCancelled = true)
        }

        assertEquals(GalvaPurchaseResult.Cancelled, purchase())
    }

    @Test
    fun `a pending payment is a pending result`() {
        revenueCat.purchaseResult = { throw PurchasesException(PurchasesError(PurchasesErrorCode.PaymentPendingError)) }

        assertEquals(GalvaPurchaseResult.Pending, purchase())
    }

    @Test
    fun `an offer RevenueCat doesn't list is reported as ineligible`() {
        revenueCat.purchaseResult = { throw GalvaPurchaseException.IneligibleForOffer() }

        assertTrue(runCatching { purchase() }.exceptionOrNull() is GalvaPurchaseException.IneligibleForOffer)
    }

    @Test
    fun `RevenueCat errors map to Galva purchase failures`() {
        assertEquals(GalvaPurchaseException.ProductUnavailable::class, failureOf(PurchasesErrorCode.ProductNotAvailableForPurchaseError))
        assertEquals(GalvaPurchaseException.PurchaseNotAllowed::class, failureOf(PurchasesErrorCode.PurchaseNotAllowedError))
        assertEquals(GalvaPurchaseException.IneligibleForOffer::class, failureOf(PurchasesErrorCode.IneligibleError))
        assertEquals(GalvaPurchaseException.NetworkError::class, failureOf(PurchasesErrorCode.NetworkError))
        assertEquals(GalvaPurchaseException.NetworkError::class, failureOf(PurchasesErrorCode.StoreProblemError))
        assertEquals(GalvaPurchaseException.VerificationFailed::class, failureOf(PurchasesErrorCode.SignatureVerificationError))
        assertEquals(GalvaPurchaseException.Failed::class, failureOf(PurchasesErrorCode.ProductAlreadyPurchasedError))
        assertEquals(GalvaPurchaseException.Failed::class, failureOf(PurchasesErrorCode.UnknownError))
    }

    @Test
    fun `a non-RevenueCat error is a generic failure`() {
        val thrown = runCatching { RevenueCatPurchaseController.result(IllegalStateException("boom")) }.exceptionOrNull()

        assertTrue(thrown is GalvaPurchaseException.Failed)
        assertEquals("boom", thrown!!.message)
    }
}
