package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.store.ZivpnStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.IOException

class HysteriaModule(service: Service) : Module<Unit>(service) {
    private val coreProcesses = mutableListOf<Process>()
    private val ready = CompletableDeferred<Unit>()

    override suspend fun run() {
        try {
            startZivpnCores()

            ready.complete(Unit)

            suspendCancellableCoroutine<Unit> { }
        } finally {
            stopZivpnCores()
        }
    }

    suspend fun waitReady() {
        ready.await()
    }

    private fun startProcessLogger(process: Process, tag: String) {
        Thread {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { Log.i("[$tag] $it") }
                }
            } catch (e: IOException) {
                // Process destroyed
            }
        }.start()
        Thread {
            try {
                process.errorStream.bufferedReader().use { reader ->
                    reader.forEachLine { Log.e("[$tag] $it") }
                }
            } catch (e: IOException) {
                // Process destroyed
            }
        }.start()
    }

    private fun startZivpnCores() {
        val nativeDir = service.applicationInfo.nativeLibraryDir
        val zivpnStore = ZivpnStore(service)
        val serverHost = zivpnStore.serverHost
        val pass = zivpnStore.serverPass
        val obfs = zivpnStore.serverObfs
        val recvWindow = zivpnStore.recvwindow
        val recvWindowConn = zivpnStore.recvwindowconn
        val upMbps = zivpnStore.up
        val downMbps = zivpnStore.down
        val coreCount = zivpnStore.coreCount

        val ports = (0 until coreCount).map { 1080 + it }
        val ranges = zivpnStore.portRanges.split(",").filter { it.isNotBlank() }.take(coreCount)

        Log.d("HysteriaModule: Starting $coreCount Hysteria Cores with Host: $serverHost")

        try {
            val libUz = "$nativeDir/libuz_core.so"
            val libLoad = "$nativeDir/libload_core.so"
            val tunnels = mutableListOf<String>()

            val shouldInclude = { v: String ->
                val t = v.trim().lowercase()
                t != "0" && t != "0 mbps" && t.isNotBlank()
            }

            for (i in 0 until coreCount) {
                val port = ports[i]
                val range = if (i < ranges.size) ranges[i] else zivpnStore.portRanges

                val config = JSONObject()
                config.put("server", "$serverHost:$range")
                config.put("obfs", obfs)
                config.put("auth", pass)
                config.put("socks5", JSONObject().put("listen", "127.0.0.1:$port"))
                config.put("insecure", true)

                if (shouldInclude(recvWindowConn)) {
                    config.put("recvwindowconn", recvWindowConn.trim().toLongOrNull() ?: recvWindowConn.trim())
                }
                if (shouldInclude(recvWindow)) {
                    config.put("recvwindow", recvWindow.trim().toLongOrNull() ?: recvWindow.trim())
                }
                if (shouldInclude(upMbps)) {
                    config.put("up", upMbps.trim())
                }
                if (shouldInclude(downMbps)) {
                    config.put("down", downMbps.trim())
                }

                val configContent = config.toString()

                val pb = ProcessBuilder(libUz, "-s", obfs, "--config", configContent)
                pb.environment()["LD_LIBRARY_PATH"] = nativeDir
                val process = pb.start()
                coreProcesses.add(process)
                startProcessLogger(process, "Hysteria-Core-$i")

                tunnels.add("127.0.0.1:$port")
            }

            val lbArgs = mutableListOf(libLoad, "-lport", "7777", "-tunnel")
            lbArgs.addAll(tunnels)
            val lbPb = ProcessBuilder(lbArgs)
            lbPb.environment()["LD_LIBRARY_PATH"] = nativeDir
            val lbProcess = lbPb.start()
            coreProcesses.add(lbProcess)
            startProcessLogger(lbProcess, "Hysteria-LB")

            Log.i("HysteriaModule: Cores started successfully")
        } catch (e: Exception) {
            Log.e("HysteriaModule: Failed to start Cores: ${e.message}", e)
        }
    }

    private fun stopZivpnCores() {
        coreProcesses.forEach {
            it.destroy()
            try {
                it.waitFor()
            } catch (e: InterruptedException) {
                // Ignore
            }
        }
        coreProcesses.clear()
        Log.i("HysteriaModule: Cores stopped")
    }
}
