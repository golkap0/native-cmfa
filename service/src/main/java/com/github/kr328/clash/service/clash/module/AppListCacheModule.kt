package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Intent
import android.content.pm.PackageInfo
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class AppListCacheModule(service: Service) : Module<Unit>(service) {
    private fun PackageInfo.uniqueUidName(): String =
        if (sharedUserId?.isNotBlank() == true) sharedUserId!! else packageName

    private fun reload() {
        val packages = service.packageManager.getInstalledPackages(0)
            .filter { it.applicationInfo != null }
            .groupBy { it.uniqueUidName() }
            .map { (_, v) ->
                val info = v[0]

                if (v.size == 1) {
                    // Force use package name if only one app in a single sharedUid group
                    // Example: firefox

                    info.applicationInfo!!.uid to info.packageName
                } else {
                    info.applicationInfo!!.uid to info.uniqueUidName()
                }
            }

        Clash.notifyInstalledAppsChanged(packages)

        Log.d("Installed ${packages.size} packages cached")
    }

    override suspend fun run() {
        delay(TimeUnit.SECONDS.toMillis(1))

        val packageChanged = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        var lastScanTime = 0L
        val debounceDelay = TimeUnit.SECONDS.toMillis(5)

        while (true) {
            // Debouncing: tunggu event package sebelum scan
            packageChanged.receive()
            
            // Delay debounce untuk mengumpulkan multiple events
            delay(debounceDelay)
            
            // Drain semua event yang pending
            while (packageChanged.tryReceive().isSuccess) {
                // consume all pending events
            }
            
            // Scan hanya jika ada perubahan
            reload()
            lastScanTime = System.currentTimeMillis()
        }
    }
}