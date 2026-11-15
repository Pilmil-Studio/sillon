package fr.pilmil.sillon

import fr.pilmil.sillon.ui.doctor.PhotoCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun discardRemovesTheFile() {
        val photo = tmp.newFile("feuille.jpg")
        assertTrue(PhotoCache.discard(photo))
        assertFalse(photo.exists())
    }

    @Test
    fun discardOnMissingFileIsHarmless() {
        val missing = java.io.File(tmp.root, "absent.jpg")
        assertTrue(PhotoCache.discard(missing))
    }
}
