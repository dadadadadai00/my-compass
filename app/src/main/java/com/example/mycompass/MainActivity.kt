package com.example.mycompass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
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
import kotlin.math.atan2
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

    // 子ども向け用の円環移動平均バッファ（手のプルプル震えをカット）
    private val ringSize = 16
    private val sinBuffer = FloatArray(ringSize)
    private val cosBuffer = FloatArray(ringSize)
    private var ringIndex = 0
    private var ringCount = 0

    // 角度管理
    private var rawAzimuth = 0f          // 精密モード用（ダイレクト）
    private var smoothAzimuth = 0f       // 子ども向け用（手ブレ除去後）
    private var kidTargetAzimuth = 0f    // 子ども向け用（東西南北の磁石吸着後）
    private var kidDisplayAzimuth = 0f   // 子ども向け用（物理演算後の表示角度）
    private var kidVelocity = 0f
    private var isKidSnapped = false
    private var lastFrameTimeMs = 0L

    // モード・持ち方状態
    private var isKidMode = false
    private var autoUprightMode = false
    private var kidLockedUpright = false // 子ども向けモード時の持ち方固定
    private var magnetAccuracyText = "計測中..."

    // UIパーツ
    private lateinit var rootLayout: LinearLayout
    private lateinit var btnPrecisionMode: TextView
    private lateinit var btnKidMode: TextView
    private lateinit var holdModeBadge: TextView
    private lateinit var compassView: CompassDialView
    private lateinit var mainTitleText: TextView
    private lateinit var subValueText: TextView
    private lateinit var sensorStatusText: TextView

    // 60fps 物理演算ループ（子ども向けモードの「スーッと通り過ぎて戻る」動き）
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

                // 重い針の不足減衰モデル（大きく通り過ぎてからユラッと収束）
                val stiffness = 26.0f
                val damping = 3.1f

                val accel = (diff * stiffness) - (kidVelocity * damping)
                kidVelocity += accel * dt
                kidDisplayAzimuth = (kidDisplayAzimuth + kidVelocity * dt + 360f) % 360f

                if (abs(diff) < 0.25f && abs(kidVelocity) < 0.5f) {
                    kidDisplayAzimuth = kidTargetAzimuth
                    kidVelocity = 0f
                }

                updateUI()
            }

            rootLayout.postOnAnimation(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val padH = dp(16)
            view.setPadding(bars.left + padH, bars.top + dp(10), bars.right + padH, bars.bottom + dp(10))
            insets
        }

        // 上段：スキン切り替えタブ ＋ 持ち方固定ボタン（比率 1.8）
        val topBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.8f
            )
        }

        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        btnPrecisionMode = TextView(this).apply {
            text = "🔬 精密モード"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                if (isKidMode) {
                    isKidMode = false
                    applySkinTheme()
                    updateUI()
                }
            }
        }

        btnKidMode = TextView(this).apply {
            text = "🧭 子ども向け"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            setOnClickListener {
                if (!isKidMode) {
                    isKidMode = true
                    kidLockedUpright = autoUprightMode
                    kidDisplayAzimuth = kidTargetAzimuth
                    kidVelocity = 0f
                    applySkinTheme()
                    updateUI()
                }
            }
        }

        tabRow.addView(btnPrecisionMode)
        tabRow.addView(btnKidMode)

        holdModeBadge = TextView(this).apply {
            text = "センサー初期化中..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            // 子ども向けモード時はタップで「平置き固定 ⇔ 立て持ち固定」を切り替え
            setOnClickListener {
                if (isKidMode) {
                    kidLockedUpright = !kidLockedUpright
                    updateUI()
                }
            }
        }

        topBox.addView(tabRow)
        topBox.addView(holdModeBadge)

        // 中段：方位磁石（比率 5.4）
        compassView = CompassDialView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 5.4f
            )
        }

        // 下段：方角・角度・センサー表示（比率 2.8）
        val bottomBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 2.8f
            )
        }

        mainTitleText = TextView(this).apply {
            text = "北"
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        subValueText = TextView(this).apply {
            text = "0°"
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        sensorStatusText = TextView(this).apply {
            text = "加速度: 待機 / 地磁気: 待機"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }

        bottomBox.addView(mainTitleText)
        bottomBox.addView(subValueText)
        bottomBox.addView(sensorStatusText)

        rootLayout.addView(topBox)
        rootLayout.addView(compassView)
        rootLayout.addView(bottomBox)
        setContentView(rootLayout)

        applySkinTheme()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    // 精密モードと子ども向けモードの見た目（スキン）を一括切り替え
    private fun applySkinTheme() {
        compassView.isKidMode = isKidMode

        if (isKidMode) {
            // 子ども向けスキン（明るく見やすい冒険コンパス風）
            rootLayout.setBackgroundColor(Color.parseColor("#FFFBEB"))

            btnPrecisionMode.background = createRoundedBg("#E2E8F0", 12)
            btnPrecisionMode.setTextColor(Color.parseColor("#64748B"))

            btnKidMode.background = createRoundedBg("#F59E0B", 12)
            btnKidMode.setTextColor(Color.WHITE)

            holdModeBadge.background = createRoundedBg("#FEF3C7", 20, "#F59E0B")
            holdModeBadge.setTextColor(Color.parseColor("#B45309"))

            mainTitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 42f)
            mainTitleText.setTextColor(Color.parseColor("#1E293B"))

            subValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            subValueText.setTextColor(Color.parseColor("#0284C7"))

            sensorStatusText.visibility = View.GONE
        } else {
            // 精密計測スキン（計器風ダークテーマ）
            rootLayout.setBackgroundColor(Color.parseColor("#0B1528"))

            btnPrecisionMode.background = createRoundedBg("#0284C7", 12)
            btnPrecisionMode.setTextColor(Color.WHITE)

            btnKidMode.background = createRoundedBg("#1E293B", 12)
            btnKidMode.setTextColor(Color.parseColor("#94A3B8"))

            holdModeBadge.background = createRoundedBg("#16243D", 20)
            holdModeBadge.setTextColor(Color.parseColor("#38BDF8"))

            mainTitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            mainTitleText.setTextColor(Color.WHITE)

            subValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            subValueText.setTextColor(Color.parseColor("#FACC15"))

            sensorStatusText.visibility = View.VISIBLE
            sensorStatusText.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun createRoundedBg(fillHex: String, radiusDp: Int, strokeHex: String? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillHex))
            if (strokeHex != null) {
                setStroke(dp(2), Color.parseColor(strokeHex))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (accelSensor == null || magnetSensor == null) {
            sensorStatusText.text = "⚠️ この端末には必要な磁気/加速度センサーがありません"
            sensorStatusText.setTextColor(Color.parseColor("#F87171"))
        }
        lastFrameTimeMs = 0L
        rootLayout.postOnAnimation(physicsRunnable)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        rootLayout.removeCallbacks(physicsRunnable)
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
        // 精密モードは素早い反応（0.25f）
        val alpha = 0.25f
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
                autoUprightMode = abs(gravity[2]) < 5.0f
                val useUpright = if (isKidMode) kidLockedUpright else autoUprightMode

                val finalMatrix = FloatArray(9)
                if (useUpright) {
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
                rawAzimuth = deg

                // 子ども向け用：円環移動平均で手のプルプル震えを完全除去
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
                smoothAzimuth = avgDeg

                // 子ども向け用：境界で暴れない「正面±22度のゆるい磁石吸着」
                kidTargetAzimuth = applyCardinalMagnet(smoothAzimuth)
            }
        }
    }

    // 東西南北の正面±22度以内に入った時だけ中心へゆるやかに引き寄せる（斜め境界は素通り）
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

        val snapRange = 22f // 吸着が始まる範囲（±22度）
        val lockRange = 9f  // 完全にピタッと止まる範囲（±9度）
        val absD = abs(minDelta)

        if (absD <= lockRange) {
            isKidSnapped = true
            return nearest
        } else if (absD < snapRange) {
            isKidSnapped = false
            // 境界で段差が出ない滑らかな3次曲線イージング
            val t = (absD - lockRange) / (snapRange - lockRange)
            val eased = t * t * t
            val adjustedDelta = minDelta * eased
            return (nearest + adjustedDelta + 360f) % 360f
        } else {
            isKidSnapped = false
            return deg
        }
    }

    private fun updateUI() {
        if (isKidMode) {
            // 子ども向けモード表示
            if (kidLockedUpright) {
                holdModeBadge.text = "📱 立て持ち固定（タップで寝かせに切替）"
            } else {
                holdModeBadge.text = "🛌 寝かせ持ち固定（タップで立て持ちに切替）"
            }

            val roundedDeg = (kidDisplayAzimuth.roundToInt() + 360) % 360
            val kidLabel = getKidDirectionLabel(roundedDeg)
            mainTitleText.text = kidLabel.first
            mainTitleText.setTextColor(kidLabel.second)

            if (isKidSnapped && abs(kidVelocity) < 3f) {
                subValueText.text = "✨ ぴったり！ ✨"
                subValueText.setTextColor(Color.parseColor("#D97706"))
            } else {
                subValueText.text = "はり が とまる のを まとう..."
                subValueText.setTextColor(Color.parseColor("#64748B"))
            }

            compassView.updateState(kidDisplayAzimuth, isKidSnapped && abs(kidVelocity) < 3f)
        } else {
            // 精密計測モード表示
            if (autoUprightMode) {
                holdModeBadge.text = "📱 立て持ちモード（背面カメラ方向基準）"
                holdModeBadge.setTextColor(Color.parseColor("#34D399"))
            } else {
                holdModeBadge.text = "🛌 平置きモード（スマホ上端基準）"
                holdModeBadge.setTextColor(Color.parseColor("#38BDF8"))
            }

            val roundedDeg = (rawAzimuth.roundToInt() + 360) % 360
            subValueText.text = "${roundedDeg}°"
            mainTitleText.text = getPrecisionDirection(roundedDeg)

            sensorStatusText.text = String.format(
                "%s\n加速度(%.1f, %.1f, %.1f) / 地磁気(%.0f, %.0f, %.0f)",
                magnetAccuracyText,
                gravity[0], gravity[1], gravity[2],
                geomagnetic[0], geomagnetic[1], geomagnetic[2]
            )

            compassView.updateState(rawAzimuth, false)
        }
    }

    private fun getPrecisionDirection(deg: Int): String {
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

    private fun getKidDirectionLabel(deg: Int): Pair<String, Int> {
        return when {
            deg >= 315 || deg < 45 -> Pair("きた（北）", Color.parseColor("#DC2626"))
            deg < 135 -> Pair("ひがし（東）", Color.parseColor("#0284C7"))
            deg < 225 -> Pair("みなみ（南）", Color.parseColor("#D97706"))
            else -> Pair("にし（西）", Color.parseColor("#059669"))
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }

    // 精密スキンと子ども向けスキンの両方を描き分けるコンパスビュー
    private class CompassDialView(context: Context) : View(context) {
        var isKidMode = false
        private var azimuth = 0f
        private var isSnappedHighlight = false

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        fun updateState(newAzimuth: Float, snapped: Boolean) {
            azimuth = newAzimuth
            isSnappedHighlight = snapped
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) * 0.43f
            if (radius <= 10f) return

            if (isKidMode) {
                drawKidCompass(canvas, cx, cy, radius)
            } else {
                drawPrecisionCompass(canvas, cx, cy, radius)
            }
        }

        // ① 精密計測スキン（既存デザイン）
        private fun drawPrecisionCompass(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
            // 上部の黄色三角マーカー
            pathPaint.color = Color.parseColor("#FACC15")
            val marker = Path().apply {
                moveTo(cx, cy - radius * 1.12f)
                lineTo(cx - radius * 0.06f, cy - radius * 1.02f)
                lineTo(cx + radius * 0.06f, cy - radius * 1.02f)
                close()
            }
            canvas.drawPath(marker, pathPaint)

            // 盤面
            fillPaint.color = Color.parseColor("#1E293B")
            canvas.drawCircle(cx, cy, radius, fillPaint)

            strokePaint.color = Color.parseColor("#38BDF8")
            strokePaint.strokeWidth = radius * 0.025f
            canvas.drawCircle(cx, cy, radius, strokePaint)

            canvas.save()
            canvas.rotate(-azimuth, cx, cy)

            // 15度刻み目盛り
            strokePaint.color = Color.parseColor("#94A3B8")
            strokePaint.strokeWidth = radius * 0.015f
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
                    strokePaint
                )
            }

            // 東西南北の文字
            textPaint.textSize = radius * 0.16f
            val textDist = radius * 0.68f
            val offsetY = (textPaint.descent() + textPaint.ascent()) / 2f

            textPaint.color = Color.parseColor("#EF4444")
            canvas.drawText("北", cx, cy - textDist - offsetY, textPaint)
            textPaint.color = Color.WHITE
            canvas.drawText("東", cx + textDist, cy - offsetY, textPaint)
            canvas.drawText("南", cx, cy + textDist - offsetY, textPaint)
            canvas.drawText("西", cx - textDist, cy - offsetY, textPaint)

            // 針
            val needleLen = radius * 0.48f
            val needleW = radius * 0.09f
            pathPaint.color = Color.parseColor("#EF4444")
            canvas.drawPath(Path().apply {
                moveTo(cx, cy - needleLen)
                lineTo(cx - needleW, cy)
                lineTo(cx + needleW, cy)
                close()
            }, pathPaint)

            pathPaint.color = Color.parseColor("#E2E8F0")
            canvas.drawPath(Path().apply {
                moveTo(cx, cy + needleLen)
                lineTo(cx - needleW, cy)
                lineTo(cx + needleW, cy)
                close()
            }, pathPaint)

            canvas.restore()

            fillPaint.color = Color.parseColor("#FACC15")
            canvas.drawCircle(cx, cy, radius * 0.06f, fillPaint)
        }

        // ② 子ども向けスキン（特大東西南北・シンプル冒険コンパス風）
        private fun drawKidCompass(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
            // 正面（12時方向）の大きなオレンジ矢印マーカー
            pathPaint.color = if (isSnappedHighlight) Color.parseColor("#16A34A") else Color.parseColor("#F59E0B")
            val marker = Path().apply {
                moveTo(cx, cy - radius * 1.14f)
                lineTo(cx - radius * 0.10f, cy - radius * 1.00f)
                lineTo(cx + radius * 0.10f, cy - radius * 1.00f)
                close()
            }
            canvas.drawPath(marker, pathPaint)

            // 明るい白盤面 ＋ 太いカラフル外枠（ぴったり合うと枠が緑に光る）
            fillPaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, radius, fillPaint)

            strokePaint.color = if (isSnappedHighlight) Color.parseColor("#22C55E") else Color.parseColor("#0284C7")
            strokePaint.strokeWidth = radius * 0.055f
            canvas.drawCircle(cx, cy, radius, strokePaint)

            // 内側の薄いガイド円
            strokePaint.color = Color.parseColor("#E0F2FE")
            strokePaint.strokeWidth = radius * 0.02f
            canvas.drawCircle(cx, cy, radius * 0.86f, strokePaint)

            canvas.save()
            canvas.rotate(-azimuth, cx, cy)

            // 東西南北だけの大きな丸ドット（細かい目盛りは無し）
            for (angle in 0 until 360 step 90) {
                val rad = Math.toRadians(angle.toDouble())
                val dotDist = radius * 0.86f
                fillPaint.color = if (angle == 0) Color.parseColor("#EF4444") else Color.parseColor("#38BDF8")
                canvas.drawCircle(
                    cx + (dotDist * sin(rad)).toFloat(),
                    cy - (dotDist * cos(rad)).toFloat(),
                    radius * 0.035f,
                    fillPaint
                )
            }

            // 特大の「北・東・南・西」文字
            textPaint.textSize = radius * 0.23f
            val textDist = radius * 0.62f
            val offsetY = (textPaint.descent() + textPaint.ascent()) / 2f

            textPaint.color = Color.parseColor("#DC2626")
            canvas.drawText("北", cx, cy - textDist - offsetY, textPaint)

            textPaint.color = Color.parseColor("#0284C7")
            canvas.drawText("東", cx + textDist, cy - offsetY, textPaint)

            textPaint.color = Color.parseColor("#D97706")
            canvas.drawText("南", cx, cy + textDist - offsetY, textPaint)

            textPaint.color = Color.parseColor("#059669")
            canvas.drawText("西", cx - textDist, cy - offsetY, textPaint)

            // 太くて見やすい磁針（赤＝北、水色＝南）
            val needleLen = radius * 0.36f
            val needleW = radius * 0.12f
            pathPaint.color = Color.parseColor("#EF4444")
            canvas.drawPath(Path().apply {
                moveTo(cx, cy - needleLen)
                lineTo(cx - needleW, cy)
                lineTo(cx + needleW, cy)
                close()
            }, pathPaint)

            pathPaint.color = Color.parseColor("#CBD5E1")
            canvas.drawPath(Path().apply {
                moveTo(cx, cy + needleLen)
                lineTo(cx - needleW, cy)
                lineTo(cx + needleW, cy)
                close()
            }, pathPaint)

            canvas.restore()

            // 中心の大きな黄色ピン
            fillPaint.color = Color.parseColor("#F59E0B")
            canvas.drawCircle(cx, cy, radius * 0.08f, fillPaint)
            fillPaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, radius * 0.035f, fillPaint)
        }
    }
}
