package io.galva.revenuecat

import io.galva.sdk.GalvaIntegration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RevenueCatIntegrationTest {
    private val galva = RecordingGalva()
    private val revenueCat = FakeRevenueCat()
    private val foregrounds = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    private fun TestScope.install(identity: RevenueCatIdentity = RevenueCatIdentity.FOLLOW_REVENUECAT): CoroutineScope {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        RevenueCatIntegration.install(identity, SilentLogger, revenueCat, galva, ForegroundSignals(foregrounds), scope)
        return scope
    }

    @Test
    fun `installs the purchase controller right away`() = runTest {
        val scope = install()

        assertTrue(galva.controller is RevenueCatPurchaseController)
        scope.cancel()
    }

    @Test
    fun `mirrors RevenueCat's identity into Galva`() = runTest {
        val scope = install()

        revenueCat.customers.emit(customer("\$RCAnonymousID:a", isAnonymous = true))
        revenueCat.customers.emit(customer("user_42", originalAppUserId = "\$RCAnonymousID:a"))
        revenueCat.customers.emit(customer("\$RCAnonymousID:b", isAnonymous = true, originalAppUserId = "\$RCAnonymousID:b"))
        advanceUntilIdle()

        assertEquals(
            listOf(
                "setPurchaseController",
                "attributes(\$RCAnonymousID:a,\$RCAnonymousID:a,[])",
                "identify(user_42)",
                "attributes(user_42,\$RCAnonymousID:a,[])",
                "logout",
                "attributes(\$RCAnonymousID:b,\$RCAnonymousID:b,[])",
            ),
            galva.calls,
        )
        scope.cancel()
    }

    @Test
    fun `manual identity leaves identify and logout to the app`() = runTest {
        val scope = install(RevenueCatIdentity.MANUAL)

        revenueCat.customers.emit(customer("user_42", entitlements = listOf("pro")))
        advanceUntilIdle()

        assertEquals(listOf("setPurchaseController", "attributes(user_42,user_42,[pro])"), galva.calls)
        scope.cancel()
    }

    @Test
    fun `waits for RevenueCat to be configured, then connects on the next foreground`() = runTest {
        revenueCat.isConfigured = false
        revenueCat.customers.emit(customer("user_42"))
        val scope = install()
        runCurrent()
        assertEquals(listOf("setPurchaseController"), galva.calls)

        foregrounds.emit(Unit) // still not configured
        runCurrent()
        assertEquals(listOf("setPurchaseController"), galva.calls)

        revenueCat.isConfigured = true
        foregrounds.emit(Unit)
        advanceUntilIdle()

        assertEquals(listOf("setPurchaseController", "identify(user_42)", "attributes(user_42,user_42,[])"), galva.calls)
        scope.cancel()
    }

    @Test
    fun `the public factory builds the revenuecat integration`() {
        val integration = GalvaIntegration.revenueCat()

        assertEquals("revenuecat", integration.name)
        assertEquals(GalvaRevenueCat.VERSION, integration.version)
    }
}
