package com.example.toonalarm

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZonedDateTime

object Alarms {
    val ringing = MutableStateFlow(false)
    fun prefs(c: Context) = c.getSharedPreferences("alarm", Context.MODE_PRIVATE)
    fun allowed(c: Context) = Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun operation(c: Context) = PendingIntent.getBroadcast(c, 1, Intent(c, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(c: Context, hour: Int, minute: Int, at: Long? = null) {
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val timestamp = at ?: next.toInstant().toEpochMilli()
        val show = PendingIntent.getActivity(c, 2, Intent(c, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        c.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(timestamp, show), operation(c))
        prefs(c).edit().putInt("hour", hour).putInt("minute", minute).putLong("at", timestamp).putBoolean("enabled", true).apply()
    }
    fun cancel(c: Context) {
        c.getSystemService(AlarmManager::class.java).cancel(operation(c))
        prefs(c).edit().putBoolean("enabled", false).apply()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.prefs(context).edit().putBoolean("enabled", false).apply()
        context.startForegroundService(Intent(context, RingingService::class.java))
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = Alarms.prefs(context)
        if (p.getBoolean("enabled", false) && Alarms.allowed(context)) {
            val saved = p.getLong("at", 0)
            try {
                Alarms.schedule(context, p.getInt("hour", 7), p.getInt("minute", 0),
                    if (intent.action == Intent.ACTION_BOOT_COMPLETED && saved > System.currentTimeMillis()) saved else null)
            } catch (_: SecurityException) { /* Permission may have been revoked during restoration. */ }
        }
    }
}

class RingingService : Service() {
    private var tone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("ringing", "Будильник", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            description = "Соня проснулся — пора вставать"
        })
        val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 4, Intent(this, RingingService::class.java).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(10, Notification.Builder(this, "ringing")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Соня: пора просыпаться!")
            .setContentText("Откройте будильник или нажмите «Выключить»")
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Выключить", stop).build()).build())
        Alarms.ringing.value = true
        if (tone == null) {
            tone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                if (Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
            vibrator = getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 350, 250, 900), 0))
            handler.postDelayed({ stopSelf() }, 10 * 60 * 1000L)
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tone?.stop(); vibrator?.cancel(); Alarms.ringing.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
