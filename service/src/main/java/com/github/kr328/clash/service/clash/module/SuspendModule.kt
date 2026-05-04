package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.core.Clash

class SuspendModule(service: Service) : Module<Unit>(service) {
    override suspend fun run() {
        // FORCE KEEP ALIVE: ZIVPN logic preserves core through screen state changes
        Clash.suspendCore(false)
    }
}
