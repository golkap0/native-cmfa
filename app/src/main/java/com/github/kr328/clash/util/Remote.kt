package com.github.kr328.clash.util

import android.os.DeadObjectException
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.remote.IProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

suspend fun <T> withClash(
    context: CoroutineContext = Dispatchers.IO,
    block: suspend IClashManager.() -> T
): T {
    var backoff = 5000L // Mulai dengan 5 detik, bukan 1 detik
    val maxRetryTime = TimeUnit.MINUTES.toMillis(5) // Max retry selama 5 menit
    val startTime = System.currentTimeMillis()

    while (true) {
        val remote = Remote.service.remote.get()
        val client = remote.clash()

        try {
            return withContext(context) { client.block() }
        } catch (e: DeadObjectException) {
            Log.w("Remote services panic")

            Remote.service.remote.reset(remote)

            // Cek apakah sudah melebihi max retry time
            if (System.currentTimeMillis() - startTime > maxRetryTime) {
                Log.e("Remote service crash terus menerus selama 5 menit, abort retry")
                throw IllegalStateException("Remote service tidak stabil, retry gagal setelah 5 menit")
            }

            delay(backoff)

            // Exponential backoff: 5s → 10s → 20s → 40s → 60s (max)
            backoff = (backoff * 2).coerceAtMost(60000L)
        }
    }
}

suspend fun <T> withProfile(
    context: CoroutineContext = Dispatchers.IO,
    block: suspend IProfileManager.() -> T
): T {
    var backoff = 5000L // Mulai dengan 5 detik, bukan 1 detik
    val maxRetryTime = TimeUnit.MINUTES.toMillis(5) // Max retry selama 5 menit
    val startTime = System.currentTimeMillis()

    while (true) {
        val remote = Remote.service.remote.get()
        val client = remote.profile()

        try {
            return withContext(context) { client.block() }
        } catch (e: DeadObjectException) {
            Log.w("Remote services panic")

            Remote.service.remote.reset(remote)

            // Cek apakah sudah melebihi max retry time
            if (System.currentTimeMillis() - startTime > maxRetryTime) {
                Log.e("Remote service crash terus menerus selama 5 menit, abort retry")
                throw IllegalStateException("Remote service tidak stabil, retry gagal setelah 5 menit")
            }

            delay(backoff)

            // Exponential backoff: 5s → 10s → 20s → 40s → 60s (max)
            backoff = (backoff * 2).coerceAtMost(60000L)
        }
    }
}
