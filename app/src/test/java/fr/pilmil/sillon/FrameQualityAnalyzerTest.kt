package fr.pilmil.sillon

import fr.pilmil.sillon.vision.FrameQuality
import fr.pilmil.sillon.vision.FrameQualityAnalyzer
import fr.pilmil.sillon.vision.FrameQualityStatus
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameQualityAnalyzerTest {

    private val analyzer = FrameQualityAnalyzer {}

    private fun buildPlanes(
        width: Int,
        height: Int,
        yAt: (x: Int, y: Int) -> Int,
        uVal: Int = 128,
        vVal: Int = 128,
    ): Triple<ByteBuffer, ByteBuffer, ByteBuffer> {
        val yBuf = ByteBuffer.allocate(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                yBuf.put(y * width + x, yAt(x, y).coerceIn(0, 255).toByte())
            }
        }
        val uvSize = (width / 2) * (height / 2)
        val uBuf = ByteBuffer.allocate(uvSize)
        val vBuf = ByteBuffer.allocate(uvSize)
        for (i in 0 until uvSize) {
            uBuf.put(i, uVal.coerceIn(0, 255).toByte())
            vBuf.put(i, vVal.coerceIn(0, 255).toByte())
        }
        return Triple(yBuf, uBuf, vBuf)
    }

    private fun evaluate(
        width: Int,
        height: Int,
        yBuf: ByteBuffer,
        uBuf: ByteBuffer?,
        vBuf: ByteBuffer?,
    ): FrameQuality = analyzer.evaluatePlanes(
        width = width,
        height = height,
        yBuffer = yBuf,
        yRowStride = width,
        yPixelStride = 1,
        uBuffer = uBuf,
        uRowStride = width / 2,
        uPixelStride = 1,
        vBuffer = vBuf,
        vRowStride = width / 2,
        vPixelStride = 1,
    )

    @Test
    fun frameQualityStatusEnumValuesExist() {
        val optimal = FrameQualityStatus.OPTIMAL
        val warning = FrameQualityStatus.WARNING
        val unusable = FrameQualityStatus.UNUSABLE
        assertEquals(3, FrameQualityStatus.entries.size)
        assertNotNull(optimal)
        assertNotNull(warning)
        assertNotNull(unusable)
    }

    @Test
    fun frameQualityDataClassIntegrity() {
        val quality = FrameQuality(
            status = FrameQualityStatus.OPTIMAL,
            sharpness = 18.5f,
            brightness = 140f,
            plantCoverage = 0.65f,
            tip = "Cadre optimal !",
        )
        assertEquals(FrameQualityStatus.OPTIMAL, quality.status)
        assertEquals(18.5f, quality.sharpness, 0.001f)
        assertEquals(140f, quality.brightness, 0.001f)
        assertEquals(0.65f, quality.plantCoverage, 0.001f)
        assertEquals("Cadre optimal !", quality.tip)
    }

    @Test
    fun tinyImageReturnsUnusableZeroMetrics() {
        val (y, u, v) = buildPlanes(4, 4, { _, _ -> 128 })
        val q = evaluate(4, 4, y, u, v)
        assertEquals(FrameQualityStatus.UNUSABLE, q.status)
        assertEquals(0f, q.sharpness, 0.001f)
        assertEquals(0f, q.brightness, 0.001f)
    }

    @Test
    fun uniformBlurryImageIsUnusable() {
        val (y, u, v) = buildPlanes(64, 64, { _, _ -> 130 }, uVal = 90, vVal = 90)
        val q = evaluate(64, 64, y, u, v)
        assertEquals(FrameQualityStatus.UNUSABLE, q.status)
        assertTrue(q.sharpness < 7.5f)
        assertTrue(q.tip.contains("floue", ignoreCase = true))
    }

    @Test
    fun sharpDarkImageIsUnusable() {
        val (y, u, v) = buildPlanes(64, 64, { x, y -> if ((x + y) % 2 == 0) 35 else 15 })
        val q = evaluate(64, 64, y, u, v)
        assertEquals(FrameQualityStatus.UNUSABLE, q.status)
        assertTrue(q.sharpness >= 7.5f)
        assertTrue(q.brightness < 45f)
        assertTrue(q.tip.contains("sombre", ignoreCase = true))
    }

    @Test
    fun sharpOverexposedImageIsUnusable() {
        val (y, u, v) = buildPlanes(64, 64, { x, y -> if ((x + y) % 2 == 0) 245 else 225 })
        val q = evaluate(64, 64, y, u, v)
        assertEquals(FrameQualityStatus.UNUSABLE, q.status)
        assertTrue(q.brightness > 225f)
    }

    @Test
    fun sharpWellLitWithoutGreenTriggersWarning() {
        val (y, u, v) = buildPlanes(64, 64, { x, y -> if ((x + y) % 2 == 0) 150 else 110 }, uVal = 128, vVal = 128)
        val q = evaluate(64, 64, y, u, v)
        assertEquals(FrameQualityStatus.WARNING, q.status)
        assertTrue(q.plantCoverage < 0.12f)
    }

    @Test
    fun sharpWellLitGreenLeafIsOptimal() {
        val (y, u, v) = buildPlanes(64, 64, { x, y -> if ((x + y) % 2 == 0) 150 else 110 }, uVal = 90, vVal = 90)
        val q = evaluate(64, 64, y, u, v)
        assertEquals(FrameQualityStatus.OPTIMAL, q.status)
        assertTrue(q.sharpness >= 13f)
        assertTrue(q.brightness in 45f..225f)
        assertTrue(q.plantCoverage >= 0.25f)
    }

    @Test
    fun missingUvPlanesFallsBackToHalfPlantCoverage() {
        val (y, _, _) = buildPlanes(64, 64, { x, y -> if ((x + y) % 2 == 0) 150 else 110 })
        val q = evaluate(64, 64, y, null, null)
        assertEquals(FrameQualityStatus.OPTIMAL, q.status)
        assertEquals(0.5f, q.plantCoverage, 0.001f)
    }
}
