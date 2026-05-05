package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.os.Build
import android.os.PowerManager
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class ThermalManagementModule(service: Service) : Module<Unit>(service) {
    override suspend fun run() = coroutineScope {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@coroutineScope
        }

        val powerManager = service.getSystemService(PowerManager::class.java) ?: return@coroutineScope
        val statuses = Channel<Int>(Channel.CONFLATED)

        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            statuses.trySend(status)
        }

        powerManager.addThermalStatusListener(service.mainExecutor, listener)
        statuses.trySend(powerManager.currentThermalStatus)

        try {
            launch {
                while (true) {
                    val status = statuses.receive()
                    if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                        Clash.suspendCore(true)
                        Log.w("Thermal warning: status=$status, core suspended")
                    }
                }
            }
        } finally {
            powerManager.removeThermalStatusListener(listener)
            statuses.close()
        }
    }
}
