package io.galva.revenuecat

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * "The app came to the foreground" events: when the integration re-checks whether RevenueCat is
 * configured yet and, once connected, re-attaches to RevenueCat's customer listener. The observer
 * is registered only while [events] is collected.
 */
internal class ForegroundSignals(val events: Flow<Unit>) {
    companion object {
        /** Driven by the process lifecycle (`ON_START`). */
        fun app(): ForegroundSignals = ForegroundSignals(
            callbackFlow {
                val lifecycle = ProcessLifecycleOwner.get().lifecycle
                val observer = object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        trySend(Unit)
                    }
                }
                // Replays ON_START right away when the app is already in the foreground.
                lifecycle.addObserver(observer)
                awaitClose { lifecycle.removeObserver(observer) }
            }
                // Lifecycle observers are added and removed on the main thread.
                .flowOn(Dispatchers.Main.immediate)
                .buffer(1, BufferOverflow.DROP_OLDEST)
        )
    }
}
