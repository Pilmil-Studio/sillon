package fr.pilmil.sillon.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.roundToInt

/** Boîte normalisée [0..1] issue du détecteur YOLO embarqué. */
data class DetectedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
    val classIndex: Int,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
}

/**
 * Pré-localiseur YOLO11n hors-ligne (`models/sillon_detector.tflite`).
 * Repère la feuille ou la zone symptomatique principale pour recentrer le classifieur 288 px
 * lorsque la photo a été prise avec du recul.
 */
class LeafDetector private constructor(
    private val interpreter: Interpreter,
) : AutoCloseable {

    private val lock = Any()
    private var closed = false

    fun detectBestBox(bitmap: Bitmap, minScore: Float = DEFAULT_MIN_SCORE): DetectedBox? {
        val inTensor = interpreter.getInputTensor(0)
        val inShape = inTensor.shape() // [1, H, W, 3]
        if (inShape.size < 4) return null
        val inputH = inShape[1]
        val inputW = inShape[2]

        val scaled = Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)
        val pixels = IntArray(inputW * inputH)
        scaled.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)

        val inType = inTensor.dataType()
        val inQuant = inTensor.quantizationParams()
        val inputBuffer = buildInputBuffer(pixels, inType, inQuant.scale, inQuant.zeroPoint)

        val outTensor = interpreter.getOutputTensor(0)
        val outShape = outTensor.shape() // [1, channels, boxes] or [1, boxes, channels]
        val outType = outTensor.dataType()
        val outQuant = outTensor.quantizationParams()

        val dim1 = outShape.getOrElse(1) { 0 }
        val dim2 = outShape.getOrElse(2) { 0 }
        val totalOut = dim1 * dim2
        if (totalOut <= 0) return null

        val rawOut = ByteBuffer.allocateDirect(totalOut * outType.byteSize()).order(ByteOrder.nativeOrder())
        synchronized(lock) {
            if (closed) return null
            interpreter.run(inputBuffer, rawOut)
        }
        rawOut.rewind()

        val floats = FloatArray(totalOut)
        when (outType) {
            DataType.FLOAT32 -> rawOut.asFloatBuffer().get(floats)
            DataType.INT8 -> {
                val scale = if (outQuant.scale > 0f) outQuant.scale else 1f
                val zp = outQuant.zeroPoint
                for (i in 0 until totalOut) floats[i] = (rawOut.get().toInt() - zp) * scale
            }
            DataType.UINT8 -> {
                val scale = if (outQuant.scale > 0f) outQuant.scale else 1f
                val zp = outQuant.zeroPoint
                for (i in 0 until totalOut) floats[i] = ((rawOut.get().toInt() and 0xFF) - zp) * scale
            }
            else -> return null
        }

        return parseBestBox(floats, dim1, dim2, inputW, inputH, minScore)
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            interpreter.close()
        }
    }

    companion object {
        private const val MODEL = "models/sillon_detector.tflite"
        private const val DEFAULT_MIN_SCORE = 0.25f

        fun isAvailable(context: Context): Boolean =
            runCatching { context.assets.list("models")?.contains("sillon_detector.tflite") == true }.getOrDefault(false)

        fun load(context: Context): LeafDetector? = runCatching {
            if (!isAvailable(context)) return null
            LeafDetector(Interpreter(mapModel(context), Interpreter.Options().setNumThreads(2)))
        }.getOrNull()

        private fun mapModel(context: Context): MappedByteBuffer =
            context.assets.openFd(MODEL).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }

        private fun buildInputBuffer(
            pixels: IntArray,
            dataType: DataType,
            scale: Float,
            zeroPoint: Int,
        ): ByteBuffer {
            val buf = ByteBuffer.allocateDirect(pixels.size * 3 * dataType.byteSize()).order(ByteOrder.nativeOrder())
            when (dataType) {
                DataType.FLOAT32 -> {
                    val fb = buf.asFloatBuffer()
                    for (px in pixels) {
                        fb.put(((px ushr 16) and 0xFF) / 255f)
                        fb.put(((px ushr 8) and 0xFF) / 255f)
                        fb.put((px and 0xFF) / 255f)
                    }
                }
                DataType.INT8 -> {
                    val s = if (scale > 0f) scale else (1f / 255f)
                    for (px in pixels) {
                        buf.put((((((px ushr 16) and 0xFF) / 255f) / s).roundToInt() + zeroPoint).coerceIn(-128, 127).toByte())
                        buf.put((((((px ushr 8) and 0xFF) / 255f) / s).roundToInt() + zeroPoint).coerceIn(-128, 127).toByte())
                        buf.put(((((px and 0xFF) / 255f) / s).roundToInt() + zeroPoint).coerceIn(-128, 127).toByte())
                    }
                }
                DataType.UINT8 -> {
                    val s = if (scale > 0f) scale else (1f / 255f)
                    for (px in pixels) {
                        buf.put((((((px ushr 16) and 0xFF) / 255f) / s).roundToInt() + zeroPoint).coerceIn(0, 255).toByte())
                        buf.put((((((px ushr 8) and 0xFF) / 255f) / s).roundToInt() + zeroPoint).coerceIn(0, 255).toByte())
                        buf.put(((((px and 0xFF) / 255f) / s).roundToInt() + zeroPoint).coerceIn(0, 255).toByte())
                    }
                }
                else -> Unit
            }
            buf.rewind()
            return buf
        }

        /**
         * Décode le tenseur YOLO11 ([1, C, N] ou [1, N, C]) et retourne la boîte la plus confiante
         * au-dessus de [minScore] avec une surface exploitable.
         */
        fun parseBestBox(
            flatOutput: FloatArray,
            dim1: Int,
            dim2: Int,
            inputW: Int,
            inputH: Int,
            minScore: Float = DEFAULT_MIN_SCORE,
            channelsFirst: Boolean = dim1 < dim2,
        ): DetectedBox? {
            if (dim1 <= 0 || dim2 <= 0) return null
            val channels = if (channelsFirst) dim1 else dim2
            val numBoxes = if (channelsFirst) dim2 else dim1
            if (channels <= 4 || flatOutput.size < channels * numBoxes) return null

            fun at(c: Int, b: Int): Float =
                if (channelsFirst) flatOutput[c * numBoxes + b] else flatOutput[b * channels + c]

            var best: DetectedBox? = null
            for (b in 0 until numBoxes) {
                var bestCls = -1
                var bestScore = minScore
                for (c in 4 until channels) {
                    val s = at(c, b)
                    if (s > bestScore) {
                        bestScore = s
                        bestCls = c - 4
                    }
                }
                if (bestCls < 0) continue

                var cx = at(0, b)
                var cy = at(1, b)
                var bw = at(2, b)
                var bh = at(3, b)
                if (cx > 2f || cy > 2f || bw > 2f || bh > 2f) {
                    cx /= inputW.toFloat()
                    cy /= inputH.toFloat()
                    bw /= inputW.toFloat()
                    bh /= inputH.toFloat()
                }
                val left = (cx - bw / 2f).coerceIn(0f, 1f)
                val top = (cy - bh / 2f).coerceIn(0f, 1f)
                val right = (cx + bw / 2f).coerceIn(0f, 1f)
                val bottom = (cy + bh / 2f).coerceIn(0f, 1f)
                val box = DetectedBox(left, top, right, bottom, bestScore, bestCls)
                if (box.area < 0.02f) continue
                if (best == null || box.score > best.score) best = box
            }
            return best
        }

        /** Recadre [bitmap] autour de [box] avec une marge de contexte (15 % par défaut). */
        fun cropWithMargin(bitmap: Bitmap, box: DetectedBox, marginRatio: Float = 0.15f): Bitmap {
            val bw = (box.right - box.left) * bitmap.width
            val bh = (box.bottom - box.top) * bitmap.height
            val padX = bw * marginRatio
            val padY = bh * marginRatio
            val rect = RectF(
                (box.left * bitmap.width - padX).coerceIn(0f, bitmap.width - 1f),
                (box.top * bitmap.height - padY).coerceIn(0f, bitmap.height - 1f),
                (box.right * bitmap.width + padX).coerceIn(1f, bitmap.width.toFloat()),
                (box.bottom * bitmap.height + padY).coerceIn(1f, bitmap.height.toFloat()),
            )
            val w = (rect.width().roundToInt()).coerceAtLeast(1)
            val h = (rect.height().roundToInt()).coerceAtLeast(1)
            return Bitmap.createBitmap(bitmap, rect.left.roundToInt(), rect.top.roundToInt(), w, h)
        }
    }
}
