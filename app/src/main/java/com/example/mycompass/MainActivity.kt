package com.example.mycompass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var magnetSensor: Sensor? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasMagnet = false

    private var targetAzimuth = 0f
    private var displayAzimuth = 0f
    private var isUprightMode = false
    private var magnetAccuracyText = "計測中..."

    private lateinit var modeBadgeText: TextView
    private lateinit var compassView: CompassDialView
    private lateinit var degreeText: TextView
    private lateinit var directionText: TextView
    private lateinit var sensorStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 全Android端末の画面サイズ・システムバーに自動フィットする1画面レイアウト
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1528"))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // 上下のカメラ穴・ナビゲーションバーを避けて内側に収める
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val padH = dp(16)
            view.setPadding(bars.left + padH, bars.top + dp(12), bars.right + padH, bars.bottom + dp(12))
            insets
        }

        // 上段：持ち方モード表示（比率 1.2）
        val topBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.2f
            )
        }
        modeBadgeText = TextView(this).apply {
            text = "センサー初期化中..."
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#16243D"))
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        topBox.addView(modeBadgeText)

        // 中段：画面サイズ自動計算の方位磁石（比率 5.6）
        compassView = CompassDialView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 5.6f
            )
        }

        // 下段：方角・角度・実機センサーテスト数値（比率 3.2）
        val bottomBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 3.2f
            )
        }

        directionText = TextView(this).apply {
            text = "北"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        degreeText = TextView(this).apply {
            text = "0°"
            setTextColor(Color.parseColor("#FACC15"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 42f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        sensorStatusText = TextView(this).apply {
            text = "加速度: 待機 / 地磁気: 待機"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }

        bottomBox.addView(directionText)
        bottomBox.addView(degreeText)
        bottomBox.addView(sensorStatusText)

        root.addView(topBox)
        root.addView(compassView)
        root.addView(bottomBox)
        setContentView(root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    override fun onResume() {
        super.onResume()
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        if (accelSensor == null || magnetSensor == null) {
            sensorStatusText.text = "⚠️ この端末には必要な磁気/加速度センサーがありません"
            sensorStatusText.setTextColor(Color.parseColor("#F87171"))
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            magnetAccuracyText = when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "精度: 高（正常）"
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "精度: 中（安定）"
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "⚠️ 精度: 低（8の字に振って補正）"
                else -> "⚠️ 磁気干渉あり（8の字補正推奨）"
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val alpha = 0.15f // 針のブレを抑える平滑化係数
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (!hasGravity) {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                    hasGravity = true
                } else {
                    for (i in 0..2) {
                        gravity[i] += alpha * (event.values[i] - gravity[i])
                    }
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (!hasMagnet) {
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                    hasMagnet = true
                } else {
                    for (i in 0..2) {
                        geomagnetic[i] += alpha * (event.values[i] - geomagnetic[i])
                    }
                }
            }
        }

        if (hasGravity && hasMagnet) {
            val rotationMatrix = FloatArray(9)
            val orientationAngles = FloatArray(3)
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                // スマホを45度以上立てて持っているか自動判定（Z軸重力が5.0未満なら立て持ち）
                isUprightMode = abs(gravity[2]) < 5.0f

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
                targetAzimuth = deg

                // 359°と0°の境界で逆回転しない最短角度補間
                var diff = targetAzimuth - displayAzimuth
                while (diff < -180f) diff += 360f
                while (diff > 180f) diff -= 360f
                displayAzimuth = (displayAzimuth + diff * 0.25f + 360f) % 360f

                updateUI()
            }
        }
    }

    private fun updateUI() {
        val roundedDeg = displayAzimuth.roundToInt() % 360
        degreeText.text = "${roundedDeg}°"
        directionText.text = getJapaneseDirection(roundedDeg)

        if (isUprightMode) {
            modeBadgeText.text = "📱 立て持ちモード（背面カメラ方向が基準）"
            modeBadgeText.setTextColor(Color.parseColor("#34D399"))
        } else {
            modeBadgeText.text = "🛌 平置きモード（スマホ上端が基準）"
            modeBadgeText.setTextColor(Color.parseColor("#38BDF8"))
        }

        sensorStatusText.text = String.format(
            "%s\n加速度(%.1f, %.1f, %.1f) / 地磁気(%.0f, %.0f, %.0f)",
            magnetAccuracyText,
            gravity[0], gravity[1], gravity[2],
            geomagnetic[0], geomagnetic[1], geomagnetic[2]
        )

        compassView.updateAzimuth(displayAzimuth)
    }

    private fun getJapaneseDirection(deg: Int): String {
        return when {
            deg >= 338 || deg < 23 -> "北 (N)"
            deg < 68 -> "北東 (NE)"
            deg < 113 -> "東 (E)"
            deg < 158 -> "南東 (SE)"
            deg < 203 -> "南 (S)"
            deg < 248 -> "南西 (SW)"
            deg < 293 -> "西 (W)"
            else -> "北西 (NW)"
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }

    // どのスマホの縦横サイズでも自動フィットするコンパス描画ビュー
    private class CompassDialView(context: Context) : View(context) {
        private var azimuth = 0f

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
        }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            style = Paint.Style.STROKE
        }
        private val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val northNeedlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF4444")
            style = Paint.Style.FILL
        }
        private val southNeedlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.FILL
        }
        private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FACC15")
            style = Paint.Style.FILL
        }
        private val forwardMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FACC15")
            style = Paint.Style.FILL
        }

        fun updateAzimuth(newAzimuth: Float) {
            azimuth = newAzimuth
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            // 画面の横幅と高さの小さい方に合わせて自動スケーリング
            val radius = min(width, height) * 0.43f
            if (radius <= 10f) return

            borderPaint.strokeWidth = radius * 0.025f
            tickPaint.strokeWidth = radius * 0.015f
            cardinalPaint.textSize = radius * 0.16f

            // スマホ正面の基準マーカー（12時方向の黄色い三角）
            val markerPath = Path().apply {
                moveTo(cx, cy - radius * 1.12f)
                lineTo(cx - radius * 0.06f, cy - radius * 1.02f)
                lineTo(cx + radius * 0.06f, cy - radius * 1.02f)
                close()
            }
            canvas.drawPath(markerPath, forwardMarkerPaint)

            // 外枠の円
            canvas.drawCircle(cx, cy, radius, ringPaint)
            canvas.drawCircle(cx, cy, radius, borderPaint)

            // 方位盤を現在の方位角分だけ逆回転させて北を常に実際の北へ向ける
            canvas.save()
            canvas.rotate(-azimuth, cx, cy)

            // 15度ごとの目盛り
            for (angle in 0 until 360 step 15) {
                val rad = Math.toRadians(angle.toDouble())
                val isMajor = (angle % 90 == 0)
                val inner = radius * (if (isMajor) 0.82f else 0.90f)
                val outer = radius * 0.96f
                canvas.drawLine(
                    cx + (inner * sin(rad)).toFloat(),
                    cy - (inner * cos(rad)).toFloat(),
                    cx + (outer * sin(rad)).toFloat(),
                    cy - (outer * cos(rad)).toFloat(),
                    tickPaint
                )
            }

            // 東西南北の文字
            val textDist = radius * 0.68f
            val textOffsetY = (cardinalPaint.descent() + cardinalPaint.ascent()) / 2f

            cardinalPaint.color = Color.parseColor("#EF4444")
            canvas.drawText("北", cx, cy - textDist - textOffsetY, cardinalPaint)

            cardinalPaint.color = Color.WHITE
            canvas.drawText("東", cx + textDist, cy - textOffsetY, cardinalPaint)
            canvas.drawText("南", cx, cy + textDist - textOffsetY, cardinalPaint)
            canvas.drawText("西", cx - textDist, cy - textOffsetY, cardinalPaint)

            // 磁針（赤＝北、白＝南）
            val needleLen = radius * 0.48f
            val needleWidth = radius * 0.09f

            val northPath = Path().apply {
                moveTo(cx, cy - needleLen)
                lineTo(cx - needleWidth, cy)
                lineTo(cx + needleWidth, cy)
                close()
            }
            canvas.drawPath(northPath, northNeedlePaint)

            val southPath = Path().apply {
                moveTo(cx, cy + needleLen)
                lineTo(cx - needleWidth, cy)
                lineTo(cx + needleWidth, cy)
                close()
            }
            canvas.drawPath(southPath, southNeedlePaint)

            canvas.restore()

            // 中心の軸ピン
            canvas.drawCircle(cx, cy, radius * 0.06f, centerDotPaint)
        }
    }
}
