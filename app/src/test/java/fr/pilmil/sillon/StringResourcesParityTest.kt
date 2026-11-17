package fr.pilmil.sillon

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Les six fichiers de chaînes de l'interface doivent avoir les mêmes clés et les mêmes marqueurs de formatage. */
class StringResourcesParityTest {

    private val dirs = listOf("values", "values-en", "values-de", "values-es", "values-it", "values-nl")
    private val marker = Regex("%(\\d+\\$)?[sdf]")

    private fun resourcesRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "shared/src/commonMain/composeResources")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("composeResources introuvable")
    }

    /** Clé (type + nom) -> marqueurs de formatage rencontrés, plus le nombre d'éléments pour un tableau. */
    private fun parse(dirName: String): Map<String, Set<String>> {
        val file = File(resourcesRoot(), "$dirName/strings.xml")
        assertTrue("$file manquant", file.isFile)
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val result = mutableMapOf<String, Set<String>>()
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            val key = "${node.tagName}:${node.getAttribute("name")}"
            val markers = marker.findAll(node.textContent).map { it.value }.toMutableSet()
            if (node.tagName == "string-array") markers += "items=${node.getElementsByTagName("item").length}"
            assertTrue("Clé en double $key dans $dirName", key !in result)
            result[key] = markers
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
