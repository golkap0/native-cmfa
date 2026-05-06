package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

class SuspendModule(service: Service) : Module<Unit>(service) {
    override suspend fun run() {
        val powerManager = service.getSystemService<PowerManager>()
        var isInteractive = powerManager?.isInteractive ?: true
        var isPowerSaveMode = powerManager?.isPowerSaveMode ?: false
        var lastSuspendState: Boolean? = null

        fun applySuspendState() {
            val shouldSuspend = !isInteractive || isPowerSaveMode
            if (lastSuspendState != shouldSuspend) {
                lastSuspendState = shouldSuspend
                Clash.suspendCore(shouldSuspend)
            }
        }

        applySuspendState()

        val screenToggle = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }

        try {
            while (true) {
                when (screenToggle.receive().action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isInteractive = true
                        applySuspendState()

                        Log.d("Clash resumed")
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isInteractive = false
                        applySuspendState()

                        Log.d("Clash suspended")
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        isPowerSaveMode = powerManager?.isPowerSaveMode ?: false
                        applySuspendState()

                        Log.d("Clash suspend state changed: interactive=$isInteractive powerSave=$isPowerSaveMode")
                    }
                    else -> {
                        // unreachable

                        Clash.healthCheckAll()
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                Clash.suspendCore(false)
            }
        }
    }
}
