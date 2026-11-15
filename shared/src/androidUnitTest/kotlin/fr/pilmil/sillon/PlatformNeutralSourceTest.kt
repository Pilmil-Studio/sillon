package fr.pilmil.sillon

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformNeutralSourceTest {

    private val forbiddenTokens = listOf(
        "import java.",
        "System.currentTimeMillis",
        "Math.toDegrees",
        "Math.toRadians",
        "Math.floorMod",
        "Math.floorDiv",
        "String.format",
    )

    @Test
    fun commonMainDoesNotUseJvmOnlyApis() {
        val commonMainDir = listOf(
            File("src/commonMain/kotlin"),
            File("shared/src/commonMain/kotlin"),
        ).firstOrNull { it.isDirectory }
            ?: error("Répertoire commonMain introuvable depuis ${File(".").absolutePath}")

        val ktFiles = commonMainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue(ktFiles.isNotEmpty(), "Aucun fichier Kotlin trouvé dans ${commonMainDir.path}")

        val violations = mutableListOf<String>()
        for (file in ktFiles) {
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trimStart()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                for (token in forbiddenTokens) {
                    if (token in line) {
                        violations += "${file.relativeTo(commonMainDir).path}:${index + 1} -> $token"
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "API JVM détectée(s) dans commonMain :\n${violations.joinToString("\n")}",
        )
    }
}
