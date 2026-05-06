package com.github.kr328.clash.service.clash.module

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficUpload
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.StatusProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit

class DynamicNotificationModule(service: Service) : Module<Unit>(service) {
    private val builder = NotificationCompat.Builder(service, StaticNotificationModule.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_logo_service)
        .setOngoing(true)
        .setColor(service.getColorCompat(R.color.color_clash))
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setContentTitle("Not Selected")
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(
            PendingIntent.getActivity(
                service,
                R.id.nf_clash_status,
                Intent().setComponent(Components.MAIN_ACTIVITY)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
            )
        )

    private val notificationManager = NotificationManagerCompat.from(service)

    private fun update() {
        val now = Clash.queryTrafficNow()
        val total = Clash.queryTrafficTotal()

        val uploading = now.trafficUpload()
        val downloading = now.trafficDownload()
        val uploaded = total.trafficUpload()
        val downloaded = total.trafficDownload()

        val notification = builder
            .setContentText(
                service.getString(
                    R.string.clash_notification_content,
                    "$uploading/s", "$downloading/s"
                )
            )
            .setSubText(
                service.getString(
                    R.string.clash_notification_content,
                    uploaded, downloaded
                )
            )
            .build()

        notificationManager.notify(R.id.nf_clash_status, notification)
    }

    override suspend fun run() = coroutineScope {
        val powerManager = service.getSystemService<PowerManager>()
        var isInteractive = powerManager?.isInteractive ?: true
        var isPowerSaveMode = powerManager?.isPowerSaveMode ?: false

        val systemReceiver = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }

        val profileLoaded = receiveBroadcast(capacity = Channel.CONFLATED) {
            addAction(Intents.ACTION_PROFILE_LOADED)
        }

        val ticker = ticker(TimeUnit.SECONDS.toMillis(5))

        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val shouldUpdate = isInteractive && !isPowerSaveMode

            if (!shouldUpdate) {
                // Suspend coroutine saat layar mati atau power save mode
                // untuk menghemat CPU dan battery
                select<Unit> {
                    systemReceiver.onReceive {
                        when (it.action) {
                            Intent.ACTION_SCREEN_ON ->
                                isInteractive = true
                            Intent.ACTION_SCREEN_OFF ->
                                isInteractive = false
                            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                                isPowerSaveMode = powerManager?.isPowerSaveMode ?: false
                        }
                    }
                    profileLoaded.onReceive {
                        builder.setContentTitle(StatusProvider.currentProfile ?: "Not selected")
                    }
                }
            } else {
                select<Unit> {
                    systemReceiver.onReceive {
                        when (it.action) {
                            Intent.ACTION_SCREEN_ON ->
                                isInteractive = true
                            Intent.ACTION_SCREEN_OFF ->
                                isInteractive = false
                            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                                isPowerSaveMode = powerManager?.isPowerSaveMode ?: false
                        }
                    }
                    profileLoaded.onReceive {
                        builder.setContentTitle(StatusProvider.currentProfile ?: "Not selected")
                    }
                    ticker.onReceive {
                        update()
                    }
                }
            }
        }
    }
}
