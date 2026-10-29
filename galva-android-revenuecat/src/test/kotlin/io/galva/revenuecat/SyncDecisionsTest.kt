package io.galva.revenuecat

import io.galva.sdk.ProfileProperty
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncDecisionsTest {

    private fun attributes(appUserId: String, entitlements: List<String> = emptyList(), original: String = appUserId) =
        SyncAction.SetAttributes(RevenueCatAttributes(appUserId, original, entitlements))

    @Test
    fun `anonymous launch only sends attributes`() {
        val state = SyncState(followsRevenueCatIdentity = true)

        val actions = state.apply(customer("\$RCAnonymousID:abc", isAnonymous = true))

        assertEquals(listOf(attributes("\$RCAnonymousID:abc")), actions)
    }

    @Test
    fun `logging in identifies the Galva user, then sends attributes`() {
        val state = SyncState(followsRevenueCatIdentity = true)
        state.apply(customer("\$RCAnonymousID:abc", isAnonymous = true))

        val actions = state.apply(customer("user_42", originalAppUserId = "\$RCAnonymousID:abc"))

        assertEquals(
            listOf(SyncAction.Identify("user_42"), attributes("user_42", original = "\$RCAnonymousID:abc")),
            actions,
        )
    }

    @Test
    fun `switching users without logging out logs Galva out first`() {
        val state = SyncState(followsRevenueCatIdentity = true)
        state.apply(customer("x"))

        val actions = state.apply(customer("y"))

        assertEquals(listOf(SyncAction.Logout, SyncAction.Identify("y"), attributes("y")), actions)
    }

    @Test
    fun `logging out of RevenueCat logs Galva out`() {
        val state = SyncState(followsRevenueCatIdentity = true)
        state.apply(customer("x"))

        val actions = state.apply(customer("\$RCAnonymousID:new", isAnonymous = true))

        assertEquals(listOf(SyncAction.Logout, attributes("\$RCAnonymousID:new")), actions)
    }

    @Test
    fun `an unchanged update does nothing`() {
        val state = SyncState(followsRevenueCatIdentity = true)
        state.apply(customer("x", entitlements = listOf("pro")))

        assertEquals(emptyList<SyncAction>(), state.apply(customer("x", entitlements = listOf("pro"))))
    }

    @Test
    fun `entitlement changes resend the attributes`() {
        val state = SyncState(followsRevenueCatIdentity = true)
        state.apply(customer("x"))

        assertEquals(listOf(attributes("x", listOf("pro"))), state.apply(customer("x", entitlements = listOf("pro"))))
    }

    @Test
    fun `attributes are resent after an identity change even when unchanged`() {
        val state = SyncState(followsRevenueCatIdentity = true)
        state.apply(customer("x"))
        state.apply(customer("\$RCAnonymousID:a", isAnonymous = true, originalAppUserId = "x"))

        // Same values as before the logout, but they belong to the new Galva user now.
        val actions = state.apply(customer("x"))

        assertEquals(listOf(SyncAction.Identify("x"), attributes("x")), actions)
    }

    @Test
    fun `a logOut made offline in an earlier launch logs Galva out on the next one`() {
        val state = SyncState(followsRevenueCatIdentity = true)
        state.seed("x") // Galva still has the previous user

        val launch = state.apply(customer("\$RCAnonymousID:new", isAnonymous = true))
        val nextLogIn = state.apply(customer("y"))

        assertEquals(listOf(SyncAction.Logout, attributes("\$RCAnonymousID:new")), launch)
        // A fresh Galva identity: y never inherits x's anonymous id or obfuscated account id.
        assertEquals(listOf(SyncAction.Identify("y"), attributes("y")), nextLogIn)
    }

    @Test
    fun `a launch as another RevenueCat user than Galva's switches users`() {
        val state = SyncState(followsRevenueCatIdentity = true)
        state.seed("x")

        assertEquals(listOf(SyncAction.Logout, SyncAction.Identify("y"), attributes("y")), state.apply(customer("y")))
    }

    @Test
    fun `a launch as the same user doesn't identify again`() {
        val state = SyncState(followsRevenueCatIdentity = true)
        state.seed("x")

        assertEquals(listOf(attributes("x")), state.apply(customer("x")))
    }

    @Test
    fun `manual identity ignores the seed`() {
        val state = SyncState(followsRevenueCatIdentity = false)
        state.seed("x")

        assertEquals(listOf(attributes("\$RCAnonymousID:a")), state.apply(customer("\$RCAnonymousID:a", isAnonymous = true)))
    }

    @Test
    fun `a new purchase or a renewal reconciles transactions, the first update is the baseline`() {
        val state = SyncState(followsRevenueCatIdentity = true)

        val baseline = state.apply(customer("x", purchases = mapOf("pro" to 1)))
        val newProduct = state.apply(customer("x", purchases = mapOf("pro" to 1, "plus" to 5)))
        val renewal = state.apply(customer("x", purchases = mapOf("pro" to 9, "plus" to 5)))
        val unchanged = state.apply(customer("x", purchases = mapOf("pro" to 9, "plus" to 5)))

        assertEquals(false, SyncAction.ReconcileTransactions in baseline)
        assertEquals(listOf(SyncAction.ReconcileTransactions), newProduct)
        assertEquals(listOf(SyncAction.ReconcileTransactions), renewal)
        assertEquals(emptyList<SyncAction>(), unchanged)
    }

    @Test
    fun `purchases disappearing on logout don't reconcile, and reconcile comes after identity changes`() {
        val state = SyncState(followsRevenueCatIdentity = true)
        state.apply(customer("x", purchases = mapOf("pro" to 1)))

        val logout = state.apply(customer("\$RCAnonymousID:a", isAnonymous = true))
        val logIn = state.apply(customer("y", purchases = mapOf("plus" to 2)))

        assertEquals(listOf(SyncAction.Logout, attributes("\$RCAnonymousID:a")), logout)
        assertEquals(listOf(SyncAction.Identify("y"), attributes("y"), SyncAction.ReconcileTransactions), logIn)
    }

    @Test
    fun `manual identity never identifies or logs out`() {
        val state = SyncState(followsRevenueCatIdentity = false)

        val actions = listOf(customer("x"), customer("y"), customer("\$RCAnonymousID:a", isAnonymous = true))
            .flatMap(state::apply)

        assertEquals(listOf(attributes("x"), attributes("y"), attributes("\$RCAnonymousID:a")), actions)
    }

    @Test
    fun `attributes become Galva properties with the iOS keys`() {
        val properties = RevenueCatAttributes("user_42", "\$RCAnonymousID:abc", listOf("pro")).toProperties()

        assertEquals(
            listOf("\$gv_rc_appUserId", "\$gv_rc_originalAppUserId", "\$gv_rc_activeEntitlements"),
            properties.map { it.key },
        )
        assertEquals(listOf("pro"), (properties.last() as ProfileProperty.Custom).value)
    }
}
