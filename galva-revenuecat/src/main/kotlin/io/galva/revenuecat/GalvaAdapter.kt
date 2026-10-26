package io.galva.revenuecat

import io.galva.sdk.Galva
import io.galva.sdk.GalvaPurchaseController

/**
 * The Galva calls the customer sync and integration make, behind an interface so tests can record
 * them. Galva's public API is thread-safe and returns immediately.
 *
 * Unlike iOS there is no `reconcileTransactions`: Galva's backend links Play purchases to users
 * itself, so a RevenueCat purchase change needs no call into the SDK.
 */
internal interface GalvaAdapter {
    fun identify(userId: String)
    fun logout()
    fun setAttributes(attributes: RevenueCatAttributes)
    fun setPurchaseController(controller: GalvaPurchaseController)
}

internal class LiveGalvaAdapter : GalvaAdapter {
    override fun identify(userId: String) = Galva.instance.identify(userId)

    override fun logout() = Galva.instance.logout()

    override fun setAttributes(attributes: RevenueCatAttributes) =
        Galva.instance.updateProperties(*attributes.toProperties().toTypedArray())

    override fun setPurchaseController(controller: GalvaPurchaseController) =
        Galva.instance.setPurchaseController(controller)
}
