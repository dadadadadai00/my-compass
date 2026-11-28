package com.example.mycompass

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    companion object {
        private const val TARGET_URL = "https://ai-island-self.vercel.app"
        private const val REQ_LOCATION_PERMISSION = 1001
    }

    private lateinit var rootContainer: FrameLayout
    private lateinit var webView: WebView

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var magnetSensor: Sensor? = null
    private var locationManager: LocationManager? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasMagnet = false

    // 子ども向けモード用：円環移動平均バッファ（手ブレ除去）
    private val ringSize = 16
    private val sinBuffer = FloatArray(ringSize)
    private val cosBuffer = FloatArray(ringSize)
    private var ringIndex = 0
    private var ringCount = 0

    // WebView（JS側）へリアルタイム供給するスレッドセーフ状態値
    @Volatile private var precisionAzimuth = 0f
    @Volatile private var kidTargetAzimuth = 0f
    @Volatile private var kidDisplayAzimuth = 0f
    @Volatile private var kidSnapped = false
    @Volatile private var isUprightMode = false
    @Volatile private var postureMode = 0 // 0:自動判定, 1:寝かせ持ち固定, 2:立て持ち固定
    @Volatile private var magnetAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

    @Volatile private var latitude = 35.68
    @Volatile private var longitude = 139.77
    @Volatile private var hasGps = false

    private var kidVelocity = 0f
    private var lastFrameTimeMs = 0L

    // 60fps 物理演算ループ（子ども向けコンパスの「スーッと通り過ぎて戻る」動き）
    private val physicsRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            if (lastFrameTimeMs == 0L) lastFrameTimeMs = now
            val dt = ((now - lastFrameTimeMs) / 1000f).coerceIn(0.001f, 0.05f)
            lastFrameTimeMs = now

            if (hasGravity && hasMagnet) {
                var diff = kidTargetAzimuth - kidDisplayAzimuth
                while (diff < -180f) diff += 360f
                while (diff > 180f) diff -= 360f

                val stiffness = 26.0f
                val damping = 3.1f
                val accel = (diff * stiffness) - (kidVelocity * damping)
                kidVelocity += accel * dt
                kidDisplayAzimuth = (kidDisplayAzimuth + kidVelocity * dt + 360f) % 360f

                if (abs(diff) < 0.25f && abs(kidVelocity) < 0.5f) {
                    kidDisplayAzimuth = kidTargetAzimuth
                    kidVelocity = 0f
                }
            }

            rootContainer.postOnAnimation(this)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0B1528"))
        }

        // 上下のステータスバー・ナビゲーションバーとUIが重ならないよう余白を確保
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0B1528"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                setGeolocationEnabled(true)
            }

            addJavascriptInterface(NativeCompassBridge(), "AndroidCompass")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val host = uri.host ?: ""
                    if (host.contains("ai-island-self.vercel.app")) {
                        return false
                    }
                    return try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, true, false)
                }
            }
        }

        rootContainer.addView(webView)
        setContentView(rootContainer)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        checkAndStartLocation(requestIfMissing = true)
        webView.loadUrl(TARGET_URL)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        checkAndStartLocation(requestIfMissing = false)
        lastFrameTimeMs = 0L
        rootContainer.postOnAnimation(physicsRunnable)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        sensorManager.unregisterListener(this)
        rootContainer.removeCallbacks(physicsRunnable)
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun checkAndStartLocation(requestIfMissing: Boolean) {
        if (!hasLocationPermission()) {
            if (requestIfMissing) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    REQ_LOCATION_PERMISSION
                )
            }
            return
        }

        val lm = locationManager ?: return
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            var bestLoc: Location? = null
            for (p in providers) {
                if (lm.isProviderEnabled(p)) {
                    val last = lm.getLastKnownLocation(p)
                    if (last != null && (bestLoc == null || last.time > bestLoc.time)) {
                        bestLoc = last
                    }
                    lm.requestLocationUpdates(p, 5000L, 10f, this)
                }
            }
            bestLoc?.let { onLocationChanged(it) }
        } catch (_: SecurityException) {}
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION_PERMISSION && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            checkAndStartLocation(requestIfMissing = false)
        }
    }

    override fun onLocationChanged(location: Location) {
        latitude = location.latitude
        longitude = location.longitude
        hasGps = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            magnetAccuracy = accuracy
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val alpha = 0.25f
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (!hasGravity) {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                    hasGravity = true
                } else {
                    for (i in 0..2) gravity[i] += alpha * (event.values[i] - gravity[i])
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (!hasMagnet) {
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                    hasMagnet = true
                } else {
                    for (i in 0..2) geomagnetic[i] += alpha * (event.values[i] - geomagnetic[i])
                }
            }
        }

        if (hasGravity && hasMagnet) {
            val rotationMatrix = FloatArray(9)
            val orientationAngles = FloatArray(3)
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                when (postureMode) {
                    1 -> isUprightMode = false
                    2 -> isUprightMode = true
                    else -> {
                        val z = abs(gravity[2])
                        if (!isUprightMode && z < 4.2f) isUprightMode = true
                        else if (isUprightMode && z > 5.8f) isUprightMode = false
                    }
                }

                val finalMatrix = FloatArray(9)
                if (isUprightMode) {
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        finalMatrix
                    )
                } else {
                    System.arraycopy(rotationMatrix, 0, finalMatrix, 0, 9)
                }

                SensorManager.getOrientation(finalMatrix, orientationAngles)
                var deg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (deg < 0f) deg += 360f
                precisionAzimuth = deg

                // 子ども向け用：円環移動平均（手ブレ完全除去）
                val rad = Math.toRadians(deg.toDouble())
                sinBuffer[ringIndex] = sin(rad).toFloat()
                cosBuffer[ringIndex] = cos(rad).toFloat()
                ringIndex = (ringIndex + 1) % ringSize
                if (ringCount < ringSize) ringCount++

                var sumSin = 0f
                var sumCos = 0f
                for (i in 0 until ringCount) {
                    sumSin += sinBuffer[i]
                    sumCos += cosBuffer[i]
                }
                var avgDeg = Math.toDegrees(atan2(sumSin.toDouble(), sumCos.toDouble())).toFloat()
                if (avgDeg < 0f) avgDeg += 360f

                // 子ども向け用：東西南北の正面±22度磁石吸着
                kidTargetAzimuth = applyCardinalMagnet(avgDeg)
            }
        }
    }

    private fun applyCardinalMagnet(deg: Float): Float {
        val cardinals = floatArrayOf(0f, 90f, 180f, 270f)
        var nearest = 0f
        var minDelta = 999f

        for (c in cardinals) {
            var d = deg - c
            while (d < -180f) d += 360f
            while (d > 180f) d -= 360f
            if (abs(d) < abs(minDelta)) {
                minDelta = d
                nearest = c
            }
        }

        val snapRange = 22f
        val lockRange = 9f
        val absD = abs(minDelta)

        return when {
            absD <= lockRange -> {
                kidSnapped = true
                nearest
            }
            absD < snapRange -> {
                kidSnapped = false
                val t = (absD - lockRange) / (snapRange - lockRange)
                val eased = t * t * t
                (nearest + minDelta * eased + 360f) % 360f
            }
            else -> {
                kidSnapped = false
                deg
            }
        }
    }

    inner class NativeCompassBridge {
        @JavascriptInterface
        fun getSensorJson(): String {
            return String.format(
                Locale.US,
                "{\"p\":%.2f,\"k\":%.2f,\"ks\":%d,\"up\":%d,\"acc\":%d,\"lat\":%.5f,\"lon\":%.5f,\"gps\":%d}",
                precisionAzimuth,
                kidDisplayAzimuth,
                if (kidSnapped) 1 else 0,
                if (isUprightMode) 1 else 0,
                magnetAccuracy,
                latitude,
                longitude,
                if (hasGps) 1 else 0
            )
        }

        @JavascriptInterface
        fun setPostureMode(mode: Int) {
            postureMode = mode.coerceIn(0, 2)
        }

        @JavascriptInterface
        fun requestGps() {
            runOnUiThread {
                checkAndStartLocation(requestIfMissing = true)
            }
        }

        @JavascriptInterface
        fun reloadApp() {
            runOnUiThread {
                webView.reload()
            }
        }
    }
}
