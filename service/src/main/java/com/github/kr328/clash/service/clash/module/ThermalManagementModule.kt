package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select

class ThermalManagementModule(service: Service) : Module<Unit>(service) {
    override suspend fun run() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        val powerManager = service.getSystemService<PowerManager>() ?: return
        val statuses = Channel<Int>(Channel.CONFLATED)
        val systemReceiver = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }

        var isInteractive = powerManager.isInteractive
        var isPowerSaveMode = powerManager.isPowerSaveMode
        var isThermalLimited = false
        var lastSuspendState: Boolean? = null
        var lastThermalStatus: Int? = null

        fun applySuspendState() {
            val shouldSuspend = isThermalLimited || !isInteractive || isPowerSaveMode
            if (lastSuspendState != shouldSuspend) {
                lastSuspendState = shouldSuspend
                Clash.suspendCore(shouldSuspend)
            }
        }

        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            statuses.trySend(status)
        }

        powerManager.addThermalStatusListener(service.mainExecutor, listener)
        statuses.trySend(powerManager.currentThermalStatus)

        try {
            while (true) {
                select<Unit> {
                    statuses.onReceive { status ->
                        isThermalLimited = status >= PowerManager.THERMAL_STATUS_MODERATE
                        applySuspendState()

                        if (lastThermalStatus != status) {
                            lastThermalStatus = status
                            if (isThermalLimited) {
                                Log.w("Thermal warning: status=$status, core suspended")
                            } else {
                                Log.i("Thermal status recovered: status=$status")
                            }
                        }
                    }
                    systemReceiver.onReceive { intent ->
                        when (intent.action) {
                            Intent.ACTION_SCREEN_ON -> isInteractive = true
                            Intent.ACTION_SCREEN_OFF -> isInteractive = false
                            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> isPowerSaveMode = powerManager.isPowerSaveMode
                        }

                        applySuspendState()
                    }
                }
            }
        } finally {
            powerManager.removeThermalStatusListener(listener)
            statuses.close()
        }
    }
}
