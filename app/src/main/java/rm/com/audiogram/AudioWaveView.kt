package rm.com.audiogram

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator

class AudioWaveView : View {

	constructor(context: Context?) : super(context)

	constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
		inflateAttrs(attrs)
	}

	constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
		inflateAttrs(attrs)
	}

	var unscaledData: ByteArray = byteArrayOf()
		set(value) {
			scaledData = byteArrayOf()

			downSampleAsync(value, chunksCount) {
				scaledData = it
				animateExpanding()
			}
		}

	var chunkHeight: Int = 0
		get() = if (field == 0) h else Math.abs(field)
		set(value) {
			field = value
			redrawData()
		}

	var chunkWidth: Int = 0
		get() = if (field == 0) dip(2) else Math.abs(field)
		set(value) {
			field = value
			redrawData()
		}

	var chunkSpacing: Int = dip(1)
		set(value) {
			field = Math.abs(value)
			redrawData()
		}

	var minChunkHeight: Int = dip(2)
		set(value) {
			field = Math.abs(value)
			redrawData()
		}

	var waveColor: Int = Color.BLACK
		set(value) {
			wavePaint = smoothPaint(value.withAlpha(0xAA))
			waveFilledPaint = filterPaint(value)
			postInvalidate()
		}

	var progress: Float = 0F
		set(value) {
			require(value in 0..100) { "Progress must be in 0..100" }

			field = Math.abs(value)
			postInvalidate()
		}

	var scaledData: ByteArray = ByteArray(chunksCount)
		set(value) {
			field = if (value.isEmpty()) ByteArray(chunksCount) else value
			redrawData()
		}

	val chunksCount: Int
		get() = w / chunkStep

	private val chunkStep: Int
		get() = chunkWidth + chunkSpacing

	private val centerY: Int
		get() = h / 2

	private val progressFactor: Float
		get() = progress / 100F

	private var wavePaint = smoothPaint(waveColor.withAlpha(0xAA))
	private var waveFilledPaint = filterPaint(waveColor)
	private var waveBitmap: Bitmap? = null

	private var w: Int = 0
	private var h: Int = 0

	override fun onDraw(canvas: Canvas?) {
		super.onDraw(canvas)
		val cv = canvas ?: return

		cv.transform {
			clipRect(0, 0, w, h)
			drawBitmap(waveBitmap, 0F, 0F, wavePaint)
		}

		cv.transform {
			clipRect(0F, 0F, w * progressFactor, h.toFloat())
			drawBitmap(waveBitmap, 0F, 0F, waveFilledPaint)
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()

		waveBitmap.safeRecycle()
		waveBitmap = null
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		if (changed) {
			w = right - left
			h = bottom - top

			if (waveBitmap.fits(w, h)) return

			waveBitmap.safeRecycle()
			waveBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

			redrawData()
		}
		super.onLayout(changed, left, top, right, bottom)
	}

	private fun redrawData(canvas: Canvas? = waveBitmap?.inCanvas(), factor: Float = 1.0F) {
		if (waveBitmap == null || canvas == null) return

		waveBitmap.flush()

		scaledData.forEachIndexed { i, chunk ->
			val chunkHeight = ((chunk.abs.toFloat() / Byte.MAX_VALUE) * chunkHeight).toInt()
			val clampedHeight = Math.max(chunkHeight, minChunkHeight)
			val heightDiff = (clampedHeight - minChunkHeight).toFloat()
			val animatedDiff = (heightDiff * factor).toInt()

			canvas.drawRect(rectOf(
					left = chunkSpacing / 2 + i * chunkStep,
					top = centerY - minChunkHeight - animatedDiff,
					right = chunkSpacing / 2 + i * chunkStep + chunkWidth,
					bottom = centerY + minChunkHeight + animatedDiff
			), wavePaint)
		}

		postInvalidate()
	}

	private fun animateExpanding() {
		ObjectAnimator.ofFloat(0.0F, 1.0F).apply {
			duration = 200
			interpolator = OvershootInterpolator()
			addUpdateListener { redrawData(factor = it.animatedFraction) }
		}.start()
	}

	private fun inflateAttrs(attrs: AttributeSet?) {
		val resAttrs = context.theme.obtainStyledAttributes(
				attrs,
				R.styleable.AudioWaveView,
				0,
				0
		) ?: return

		with(resAttrs) {
			chunkHeight = getDimensionPixelSize(R.styleable.AudioWaveView_chunkHeight, chunkHeight)
			chunkWidth = getDimensionPixelSize(R.styleable.AudioWaveView_chunkWidth, chunkWidth)
			chunkSpacing = getDimensionPixelSize(R.styleable.AudioWaveView_chunkSpacing, chunkSpacing)
			minChunkHeight = getDimensionPixelSize(R.styleable.AudioWaveView_minChunkHeight, minChunkHeight)
			waveColor = getColor(R.styleable.AudioWaveView_waveColor, waveColor)
			progress = getFloat(R.styleable.AudioWaveView_progress, progress)
			recycle()
		}
	}
}