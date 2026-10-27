package io.galva.revenuecat.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitSyncPurchases
import io.galva.sdk.Galva
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Control panel for the RevenueCat integration: use RevenueCat as an app would, and watch Galva
 * follow. Filter Logcat on the `Galva` tag to see the `[revenuecat]` lines.
 */
class MainActivity : ComponentActivity() {
    private val galva get() = Galva.instance

    private lateinit var status: TextView
    private lateinit var packages: LinearLayout

    // SDK calls only enqueue work, so the status panel polls instead of wiring callbacks.
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRefresher = object : Runnable {
        override fun run() {
            refreshStatus()
            statusHandler.postDelayed(this, STATUS_REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        packages = findViewById(R.id.packages)
        val userIdInput = findViewById<EditText>(R.id.user_id_input)

        // Galva follows: logIn identifies the Galva user, logOut logs Galva out.
        onClick(R.id.rc_log_in_button) {
            val userId = userIdInput.text.toString().trim()
            if (userId.isEmpty()) {
                toast(getString(R.string.toast_user_id_required))
            } else {
                revenueCat("logIn") { show(awaitLogIn(userId).customerInfo) }
            }
        }
        onClick(R.id.rc_log_out_button) { revenueCat("logOut") { show(awaitLogOut()) } }
        // A purchase from the app's own paywall reaches Galva as $gv_rc_activeEntitlements.
        onClick(R.id.load_offerings_button) { revenueCat("offerings") { showPackages(awaitOfferings().current) } }
        onClick(R.id.sync_purchases_button) { revenueCat("syncPurchases") { show(awaitSyncPurchases()) } }
        // Offers bought inside a Galva message run through RevenueCat.
        onClick(R.id.check_messages_button) { galva.checkForMessages() }
        onClick(R.id.open_deep_link_button) {
            // Goes through the intent filter like a link tapped elsewhere; lands in onNewIntent.
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SAMPLE_DEEP_LINK)).setPackage(packageName))
        }

        // Skip on recreation (e.g. rotation): the link was already handed over.
        if (savedInstanceState == null) handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        statusHandler.post(statusRefresher)
    }

    override fun onPause() {
        statusHandler.removeCallbacks(statusRefresher)
        super.onPause()
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        // false means "not a Galva link": a real app would route it itself here.
        galva.handleOpenURL(uri)
    }

    /** Runs a RevenueCat call and reports how it went; RevenueCat must be configured first. */
    private fun revenueCat(action: String, block: suspend Purchases.() -> Unit) {
        if (!Purchases.isConfigured) {
            toast(getString(R.string.toast_revenuecat_missing))
            return
        }
        lifecycleScope.launch {
            try {
                Purchases.sharedInstance.block()
                toast(getString(R.string.toast_done, action))
            } catch (e: CancellationException) {
                throw e
            } catch (e: PurchasesException) {
                toast(getString(R.string.toast_failed, action, e.message))
            }
        }
    }

    private fun show(customerInfo: CustomerInfo) {
        DemoState.customerInfo.value = customerInfo
    }

    /** One "Buy" button per package of the current offering. */
    private fun showPackages(offering: Offering?) {
        packages.removeAllViews()
        val available = offering?.availablePackages.orEmpty()
        if (available.isEmpty()) {
            toast(getString(R.string.toast_no_offerings))
            return
        }
        available.forEach { pkg ->
            packages.addView(Button(this).apply {
                text = getString(R.string.action_buy, pkg.product.title, pkg.product.price.formatted)
                setOnClickListener {
                    revenueCat("purchase") {
                        show(awaitPurchase(PurchaseParams.Builder(this@MainActivity, pkg).build()).customerInfo)
                    }
                }
            })
        }
    }

    private fun refreshStatus() {
        val revenueCatConfigured = Purchases.isConfigured
        val dash = getString(R.string.status_dash)
        val entitlements = DemoState.customerInfo.value?.entitlements?.active?.keys?.sorted().orEmpty()
        status.text = getString(
            R.string.status_format,
            Galva.sdkVersion,
            configured(galva.isConfigured),
            galva.currentUserId.ifEmpty { dash },
            getString(if (galva.isAnonymous) R.string.status_anonymous else R.string.status_identified),
            Purchases.frameworkVersion,
            configured(revenueCatConfigured),
            if (revenueCatConfigured) Purchases.sharedInstance.appUserID else dash,
            when {
                !revenueCatConfigured -> dash
                Purchases.sharedInstance.isAnonymous -> getString(R.string.status_anonymous)
                else -> getString(R.string.status_identified)
            },
            entitlements.joinToString().ifEmpty { getString(R.string.status_none) },
        )
    }

    private fun configured(isConfigured: Boolean) =
        getString(if (isConfigured) R.string.status_configured else R.string.status_not_configured)

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private inline fun onClick(id: Int, crossinline action: () -> Unit) {
        findViewById<Button>(id).setOnClickListener { action() }
    }

    private companion object {
        const val SAMPLE_DEEP_LINK =
            "gvrcdemo://openCommunication?communicationId=demo-communication&source=control-panel"
        const val STATUS_REFRESH_MS = 1_000L
    }
}
