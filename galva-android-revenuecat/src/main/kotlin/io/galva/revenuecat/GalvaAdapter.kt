package io.galva.revenuecat

import io.galva.sdk.Galva
import io.galva.sdk.GalvaPurchaseController

/**
 * The Galva calls the customer sync and integration make, behind an interface so tests can record
 * them. Galva's public API is thread-safe and returns immediately.
 */
internal interface GalvaAdapter {
    /** The Galva user ID when identified, null while anonymous. */
    val identifiedUserId: String?

    fun identify(userId: String)
    fun logout()
    fun setAttributes(attributes: RevenueCatAttributes)
    fun setPurchaseController(controller: GalvaPurchaseController)

    /** Reports the device's newest Play purchase to Galva now, like iOS's `reconcileTransactions`. */
    fun reconcileTransactions()
}

internal class LiveGalvaAdapter : GalvaAdapter {
    override val identifiedUserId: String?
        get() = Galva.instance.takeUnless { it.isAnonymous }?.currentUserId?.takeIf { it.isNotEmpty() }

    override fun identify(userId: String) = Galva.instance.identify(userId)

    override fun logout() = Galva.instance.logout()

    override fun setAttributes(attributes: RevenueCatAttributes) =
        Galva.instance.updateProperties(*attributes.toProperties().toTypedArray())

    override fun setPurchaseController(controller: GalvaPurchaseController) =
        Galva.instance.setPurchaseController(controller)

    override fun reconcileTransactions() = Galva.instance.reconcileTransactions()
}
