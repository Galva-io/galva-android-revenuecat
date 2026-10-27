package io.galva.revenuecat.demo

import android.app.Application
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import io.galva.revenuecat.revenueCat
import io.galva.sdk.Configuration
import io.galva.sdk.Environment
import io.galva.sdk.Galva
import io.galva.sdk.GalvaIntegration
import io.galva.sdk.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import com.revenuecat.purchases.LogLevel as RevenueCatLogLevel

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The two configure calls may come in either order: the integration connects to
        // RevenueCat as soon as RevenueCat is configured.
        if (BuildConfig.REVENUECAT_API_KEY.isNotBlank()) {
            Purchases.logLevel = RevenueCatLogLevel.DEBUG
            Purchases.configure(PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_API_KEY).build())
            // The app's own listener, set before Galva connects: the integration chains onto it,
            // so this one keeps receiving every update.
            Purchases.sharedInstance.updatedCustomerInfoListener =
                UpdatedCustomerInfoListener { DemoState.customerInfo.value = it }
        }
        Galva.configure(
            this,
            Configuration(
                apiKey = BuildConfig.GALVA_API_KEY,
                env = Environment.Development,
                logLevel = LogLevel.DEBUG,
                // Messages show on the visible activity; no getInAppMessage() collector needed.
                autoDisplayInAppMessages = true,
            ),
            integrations = listOf(GalvaIntegration.revenueCat()),
        )
    }
}

/** RevenueCat customer state for the status panel. */
internal object DemoState {
    val customerInfo = MutableStateFlow<CustomerInfo?>(null)
}
