package fr.pilmil.sillon

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Les chaînes Android de l'appli (notification, navigation) existent dans les six langues, avec les mêmes marqueurs. */
class AppStringResourcesParityTest {

    private val dirs = listOf("values", "values-en", "values-de", "values-es", "values-it", "values-nl")
    private val marker = Regex("%(\\d+\\$)?[sdf]")

    private fun resourcesRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/res")
            if (candidate.isDirectory) return candidate
            val local = File(dir, "src/main/res")
            if (local.isDirectory && File(local, "values/strings.xml").isFile) return local
            dir = dir.parentFile
        }
        error("res introuvable")
    }

    /** Clé (type + nom) -> marqueurs de formatage rencontrés. Les chaînes `translatable="false"` sont ignorées. */
    private fun parse(dirName: String): Map<String, Set<String>> {
        val file = File(resourcesRoot(), "$dirName/strings.xml")
        assertTrue("$file manquant", file.isFile)
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val result = mutableMapOf<String, Set<String>>()
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.getAttribute("translatable") == "false") continue
            val key = "${node.tagName}:${node.getAttribute("name")}"
            assertTrue("Clé en double $key dans $dirName", key !in result)
            result[key] = marker.findAll(node.textContent).map { it.value }.toSet()
        }
        return result
    }

    @Test
    fun allLanguagesHaveSameKeys() {
        val reference = parse(dirs.first())
        assertTrue(reference.isNotEmpty())
        for (dir in dirs.drop(1)) {
            assertEquals("Clés différentes dans $dir", reference.keys, parse(dir).keys)
        }
    }

    @Test
    fun allLanguagesHaveSameFormatMarkers() {
        val reference = parse(dirs.first())
        for (dir in dirs.drop(1)) {
            val other = parse(dir)
            for ((key, markers) in reference) {
                assertEquals("Marqueurs différents pour $key dans $dir", markers, other[key])
            }
        }
    }
}
