package fr.pilmil.sillon

import fr.pilmil.sillon.engine.PhotoVerdict
import fr.pilmil.sillon.vision.LeafDetector
import fr.pilmil.sillon.vision.PhotoClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LeafDetectorTest {

    @Test
    fun parseBestBox_handlesChannelsFirstNormalizedBoxes() {
        // dim1 = 11 (4 coords + 7 classes), dim2 = 2 boxes
        val flat = FloatArray(11 * 2)
        // Box 0: tiny box (ignored because area < 0.02) with high score
        flat[0 * 2 + 0] = 0.5f
        flat[1 * 2 + 0] = 0.5f
        flat[2 * 2 + 0] = 0.05f
        flat[3 * 2 + 0] = 0.05f
        flat[(4 + 1) * 2 + 0] = 0.95f

        // Box 1: valid box centered at (0.5, 0.5), size (0.4, 0.5), class 2 score 0.72
        flat[0 * 2 + 1] = 0.5f
        flat[1 * 2 + 1] = 0.5f
        flat[2 * 2 + 1] = 0.4f
        flat[3 * 2 + 1] = 0.5f
        flat[(4 + 2) * 2 + 1] = 0.72f

        val best = LeafDetector.parseBestBox(flat, dim1 = 11, dim2 = 2, inputW = 512, inputH = 512, minScore = 0.25f, channelsFirst = true)
        assertNotNull(best)
        assertEquals(2, best!!.classIndex)
        assertEquals(0.72f, best.score, 1e-4f)
        assertEquals(0.3f, best.left, 1e-4f)
        assertEquals(0.25f, best.top, 1e-4f)
        assertEquals(0.7f, best.right, 1e-4f)
        assertEquals(0.75f, best.bottom, 1e-4f)
    }

    @Test
    fun parseBestBox_normalizesPixelCoordinatesAndRejectsLowConfidence() {
        // dim1 = 2 boxes, dim2 = 11 channels (boxes-first)
        val flat = FloatArray(2 * 11)
        // Box 0: pixel coords in 512x512, score 0.10 (below 0.25)
        flat[0] = 256f
        flat[1] = 256f
        flat[2] = 256f
        flat[3] = 256f
        flat[5] = 0.10f

        assertNull(LeafDetector.parseBestBox(flat, dim1 = 2, dim2 = 11, inputW = 512, inputH = 512, minScore = 0.25f, channelsFirst = false))

        // Raise score to 0.60 -> normalized to [0.25..0.75]
        flat[5] = 0.60f
        val best = LeafDetector.parseBestBox(flat, dim1 = 2, dim2 = 11, inputW = 512, inputH = 512, minScore = 0.25f, channelsFirst = false)
        assertNotNull(best)
        assertEquals(0.25f, best!!.left, 1e-3f)
        assertEquals(0.75f, best.right, 1e-3f)
    }

    @Test
    fun selectBestVerdict_prefersConfidentCropOverUnsureFull() {
        val unsure = PhotoVerdict.Unsure(leads = listOf("mildiou" to 0.42f))
        val croppedDisease = PhotoVerdict.Disease(catalogId = "mildiou", confidence = 0.78f)
        assertEquals(croppedDisease, PhotoClassifier.selectBestVerdict(unsure, croppedDisease))
    }
}
