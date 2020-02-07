package com.lvaccaro.lamp

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.preference.PreferenceManager
import java.io.File
import java.util.logging.Logger

class LightningService : IntentService("LightningService") {

    val log = Logger.getLogger(LightningService::class.java.name)
    var process: Process? = null
    val NOTIFICATION_ID = 573948
    val daemon = "lightningd"

    override fun onHandleIntent(workIntent: Intent?) {
        val dataString = workIntent!!.dataString
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)

        log.info("start $daemon service")
        val lightningDir = File(rootDir(), ".lightning")
        val bitcoinDir = File(rootDir(), ".bitcoin")
        val binaryDir = rootDir()
        if (!lightningDir.exists()) {
            lightningDir.mkdir()
        }
        if (!bitcoinDir.exists()) {
            bitcoinDir.mkdir()
        }

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val network = sharedPref.getString("network", "testnet").toString()
        val logLevel = sharedPref.getString("log-level", "io").toString()
        val rpcuser = sharedPref.getString("bitcoin-rpcuser", "").toString()
        val rpcpassword = sharedPref.getString("bitcoin-rpcpassword", "").toString()
        val rpcconnect = sharedPref.getString("bitcoin-rpcconnect", "127.0.0.1").toString()
        val rpcport = sharedPref.getString("bitcoin-rpcport", "9753").toString()
        var proxy = sharedPref.getString("proxy", "").toString()
        var announceaddr = sharedPref.getString("announce-addr", "").toString()
        var bindaddr = sharedPref.getString("bind-addr", "").toString()
        var addr = sharedPref.getString("addr", "").toString()
        val alias = sharedPref.getString("alias", "").toString()

        val options = arrayListOf<String>(
            String.format("%s/%s", binaryDir.canonicalPath, daemon),
            String.format("--network=%s", network),
            String.format("--log-level=%s", logLevel),
            String.format("--lightning-dir=%s", lightningDir.path),
            String.format("--bitcoin-cli=%s/bitcoin-cli", binaryDir.canonicalPath),
            String.format("--bitcoin-datadir=%s", bitcoinDir.path),
            String.format("--plugin-dir=%s", File(binaryDir.path , "plugins").path),
            String.format("--bitcoin-rpcconnect=%s", rpcconnect),
            String.format("--bitcoin-rpcuser=%s", rpcuser),
            String.format("--bitcoin-rpcport=%s", rpcport),
            String.format("--bitcoin-rpcpassword=%s", rpcpassword))

        if (!alias.isEmpty()) {
            options.add(String.format("--alias=%s", alias))
        }

        if (sharedPref.getBoolean("enabled-tor", true)) {
            proxy = "127.0.0.1:9050"
            bindaddr = "127.0.0.1:9735"
            //addr = "statictor:127.0.0.1:9051"
            announceaddr = TorService.getHostname() ?: ""
            options.add("--always-use-proxy=true")
        }

        if (!proxy.isEmpty()) {
            options.add(String.format("--proxy=%s", proxy))
        }
        if (!announceaddr.isEmpty()) {
            options.add(String.format("--announce-addr=%s", announceaddr))
        }
        if (!bindaddr.isEmpty()) {
            options.add(String.format("--bind-addr=%s", bindaddr))
        }
        if (!addr.isEmpty()) {
            options.add(String.format("--addr=%s", addr))
        }

        val pb = ProcessBuilder(options)
        pb.directory(binaryDir)
        pb.redirectErrorStream(true)
        val logFile = File(rootDir(),"$daemon.log")
        logFile.delete()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        }
        process = pb.start()
        //return super.onStartCommand(intent, flags, startId)
        log.info("exit $daemon service")

        startForeground()
        return Service.START_STICKY
    }

    fun startForeground() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT)

        val notification = Notification.Builder(this)
            .setContentTitle(getString(R.string.app_name) + " is running")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelName = "channel_00"
            val channel = NotificationChannel(channelName, getString(R.string.app_name), importance)
            channel.enableLights(true)
            channel.enableVibration(true)
            notificationManager.createNotificationChannel(channel)
            notification.setChannelId(channelName)
        }

        startForeground(NOTIFICATION_ID, notification.build())
    }

    private fun cancelNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        log.info("destroying core service")
        process?.destroy()
        process = null
        cancelNotification()
    }
}