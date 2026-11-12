package com.offlinevoicecommander.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.offlinevoicecommander.app.MainActivity
import com.offlinevoicecommander.app.R
import com.offlinevoicecommander.app.command.CommandExecutor
import com.offlinevoicecommander.app.macro.MacroRepository
import com.offlinevoicecommander.app.state.AppState
import com.offlinevoicecommander.app.state.VoiceState
import com.offlinevoicecommander.app.voice.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service (microphone type) that owns the draggable floating command panel.
 * The notification is mandatory on Android and shows whenever the microphone may be in use.
 */
class FloatingService : Service() {

    companion object {
        private const val CHANNEL_ID = "ovc_command_center"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_QUIT = "com.offlinevoicecommander.app.action.QUIT"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FloatingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var indicator: View
    private lateinit var statusText: TextView
    private lateinit var commandText: TextView
    private lateinit var resultText: TextView
    private lateinit var body: LinearLayout
    private lateinit var minimizeButton: TextView
    private lateinit var startStopButton: TextView
    private var expanded = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        CommandExecutor.init(this)
        MacroRepository.init(this)
        windowManager = getSystemService(WindowManager::class.java)

        createChannel()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (e: Exception) {
            AppState.lastResult.value = "Could not start the command center: ${e.message}"
            stopSelf()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            AppState.lastResult.value = "Floating window permission is missing."
            stopSelf()
            return
        }

        buildOverlay()
        AppState.overlayRunning.value = true
        collectState()
        VoiceEngine.startListening(this, once = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_QUIT) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        VoiceEngine.stop()
        CommandExecutor.stopAll()
        overlay?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // already removed
            }
        }
        overlay = null
        AppState.overlayRunning.value = false
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Command center",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while the floating command center and microphone are active" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val quit = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingService::class.java).setAction(ACTION_QUIT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Offline Voice Commander")
            .setContentText("Command center active. Voice is processed on this device only.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(0, "Close", quit)
            .build()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun pill(label: String, color: Int, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(9), dp(14), dp(9))
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(20).toFloat()
        }
        contentDescription = label
        setOnClickListener { onClick() }
    }

    private fun TextView.setPillColor(color: Int) {
        (background as? GradientDrawable)?.setColor(color)
    }

    private fun buildOverlay() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F01B1F2A"))
                cornerRadius = dp(16).toFloat()
            }
        }

        // Header: indicator + title (drag handle) + minimize/expand
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        indicator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(8) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.GRAY)
            }
        }
        val title = TextView(this).apply {
            text = "Voice Commander (drag me)"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        minimizeButton = pill("-", Color.parseColor("#3A4150")) { toggleExpanded() }
        header.addView(indicator)
        header.addView(title)
        header.addView(minimizeButton)
        root.addView(header)

        // Body
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        }
        commandText = TextView(this).apply {
            setTextColor(Color.parseColor("#B8E0FF"))
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
        }
        resultText = TextView(this).apply {
            setTextColor(Color.parseColor("#C9CED8"))
            textSize = 12f
            setPadding(0, dp(2), 0, dp(8))
        }
        body.addView(statusText)
        body.addView(commandText)
        body.addView(resultText)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val micButton = pill("MIC (1 command)", Color.parseColor("#2D6CDF")) {
            if (AppState.voiceState.value == VoiceState.LISTENING) VoiceEngine.stop()
            else VoiceEngine.startListening(this, once = true)
        }
        startStopButton = pill("Start", Color.parseColor("#1F9D55")) {
            if (AppState.voiceState.value == VoiceState.LISTENING) VoiceEngine.stop()
            else VoiceEngine.startListening(this, once = false)
        }
        val rowParams = { first: Boolean ->
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (!first) marginStart = dp(8)
            }
        }
        row.addView(micButton, rowParams(true))
        row.addView(startStopButton, rowParams(false))
        body.addView(row)

        val emergency = pill("EMERGENCY STOP", Color.parseColor("#D32F2F")) {
            CommandExecutor.stopAll()
            VoiceEngine.stop()
            AppState.lastResult.value = "EMERGENCY STOP: all actions and listening stopped"
        }
        body.addView(
            emergency,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )
        root.addView(body)

        params = WindowManager.LayoutParams(
            dp(290),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(120)
        }

        header.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - touchX).toInt()
                        params.y = startY + (event.rawY - touchY).toInt()
                        try {
                            windowManager?.updateViewLayout(root, params)
                        } catch (e: Exception) {
                            // view already removed
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(root, params)
        overlay = root
    }

    private fun toggleExpanded() {
        expanded = !expanded
        body.visibility = if (expanded) View.VISIBLE else View.GONE
        minimizeButton.text = if (expanded) "-" else "+"
    }

    private fun collectState() {
        scope.launch { AppState.voiceState.collect { render(it) } }
        scope.launch { AppState.lastCommand.collect { commandText.text = "Heard: $it" } }
        scope.launch { AppState.lastResult.collect { resultText.text = it } }
    }

    private fun render(state: VoiceState) {
        val (color, text) = when (state) {
            VoiceState.LISTENING -> Color.parseColor("#2ECC71") to "Microphone ON - listening"
            VoiceState.READY -> Color.GRAY to "Microphone off - tap Start"
            VoiceState.LOADING -> Color.parseColor("#F5A623") to "Loading speech model..."
            VoiceState.NO_MODEL -> Color.parseColor("#E74C3C") to "No speech model imported"
            VoiceState.ERROR -> Color.parseColor("#E74C3C") to "Error - see message below"
        }
        (indicator.background as? GradientDrawable)?.setColor(color)
        statusText.text = text
        val listening = state == VoiceState.LISTENING
        startStopButton.text = if (listening) "Stop" else "Start"
        startStopButton.setPillColor(if (listening) Color.parseColor("#C0392B") else Color.parseColor("#1F9D55"))
    }
}
