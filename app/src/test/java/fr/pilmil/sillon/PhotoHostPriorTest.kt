package fr.pilmil.sillon

import fr.pilmil.sillon.data.HostPlausibility
import fr.pilmil.sillon.engine.PhotoDiagnosis
import fr.pilmil.sillon.engine.PhotoModelSpec
import fr.pilmil.sillon.engine.PhotoVerdict
import fr.pilmil.sillon.model.BotanicalFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoHostPriorTest {

    private fun cls(label: String, id: String? = label, group: List<String>? = null) =
        PhotoModelSpec.ModelClass(label, id, group)

    // Même ordre que le modèle embarqué pour les classes utiles ici.
    private val spec = PhotoModelSpec(
        format = 1,
        input = PhotoModelSpec.Input(224, 224),
        classes = listOf(
            cls("sain", null), cls("mildiou"), cls("oidium"), cls("autre", null),
            cls("pucerons"), cls("pieride_chou"), cls("doryphore"), cls("hors_sujet", null),
        ),
        threshold = 0.65f,
    )

    private val index = spec.classes.map { it.label }

    private fun probs(vararg pairs: Pair<String, Float>): FloatArray {
        val out = FloatArray(spec.classes.size)
        for ((label, p) in pairs) out[index.indexOf(label)] = p
        return out
    }

    @Test
    fun withoutPlantTheProbabilitiesAreUnchanged() {
        val p = probs("doryphore" to 0.5f, "sain" to 0.3f, "autre" to 0.2f)
        assertArrayEquals(p, PhotoDiagnosis.applyHostPrior(p, spec, null), 0f)
        assertEquals(PhotoDiagnosis.interpret(p, spec), PhotoDiagnosis.interpret(p, spec, null))
    }

    @Test
    fun resultIsRenormalised() {
        val p = probs("doryphore" to 0.4f, "pieride_chou" to 0.3f, "sain" to 0.2f, "autre" to 0.1f)
        val out = PhotoDiagnosis.applyHostPrior(p, spec, BotanicalFamily.SOLANACEAE)
        assertEquals(1f, out.sum(), 1e-5f)
    }

    @Test
    fun colouradoBeetleGainsOnSolanaceaeAndCabbageWhiteLosesThere() {
        val p = probs("doryphore" to 0.35f, "pieride_chou" to 0.35f, "autre" to 0.3f)
        val out = PhotoDiagnosis.applyHostPrior(p, spec, BotanicalFamily.SOLANACEAE)
        assertTrue(out[index.indexOf("doryphore")] > p[index.indexOf("doryphore")])
        assertTrue(out[index.indexOf("pieride_chou")] < 0.1f)
    }

    @Test
    fun cabbageWhiteGainsOnBrassicaceae() {
        val p = probs("doryphore" to 0.35f, "pieride_chou" to 0.35f, "autre" to 0.3f)
        val out = PhotoDiagnosis.applyHostPrior(p, spec, BotanicalFamily.BRASSICACEAE)
        assertTrue(out[index.indexOf("pieride_chou")] > out[index.indexOf("doryphore")])
    }

    @Test
    fun aClassIsNeverZeroedEvenOnAnUnexpectedHost() {
        val p = probs("doryphore" to 0.9f, "autre" to 0.1f)
        val out = PhotoDiagnosis.applyHostPrior(p, spec, BotanicalFamily.APIACEAE)
        assertTrue(out[index.indexOf("doryphore")] > 0f)
        // 0,9 x 0,05 = 0,045 contre 0,1 : le plancher garde une vraie chance à la cause.
        assertEquals(0.045f / 0.145f, out[index.indexOf("doryphore")], 1e-4f)
        assertEquals(0.05f, HostPlausibility.FLOOR, 0f)
    }

    @Test
    fun healthyOtherAndOffTopicClassesAreNotWeighted() {
        val p = probs("sain" to 0.25f, "autre" to 0.25f, "hors_sujet" to 0.25f, "doryphore" to 0.25f)
        val out = PhotoDiagnosis.applyHostPrior(p, spec, BotanicalFamily.APIACEAE)
        val ratioSain = out[index.indexOf("sain")] / p[index.indexOf("sain")]
        val ratioAutre = out[index.indexOf("autre")] / p[index.indexOf("autre")]
        val ratioHors = out[index.indexOf("hors_sujet")] / p[index.indexOf("hors_sujet")]
        assertEquals(ratioSain, ratioAutre, 1e-5f)
        assertEquals(ratioSain, ratioHors, 1e-5f)
    }

    @Test
    fun broadSpectrumCausesAreNotPenalisedWhateverTheFamily() {
        for (family in BotanicalFamily.entries) {
            for (id in listOf("pucerons", "limaces", "oidium", "rouille", "botrytis")) {
                assertEquals("$id sur $family", 1f, HostPlausibility.plausibility(id, family), 0f)
            }
        }
    }

    @Test
    fun hostSpecificCausesFollowTheCatalogFamilies() {
        assertEquals(1f, HostPlausibility.plausibility("doryphore", BotanicalFamily.SOLANACEAE), 0f)
        assertEquals(HostPlausibility.FLOOR, HostPlausibility.plausibility("doryphore", BotanicalFamily.BRASSICACEAE), 0f)
        assertEquals(1f, HostPlausibility.plausibility("pieride_chou", BotanicalFamily.BRASSICACEAE), 0f)
        assertEquals(HostPlausibility.FLOOR, HostPlausibility.plausibility("pieride_chou", BotanicalFamily.SOLANACEAE), 0f)
        assertEquals(1f, HostPlausibility.plausibility("identifiant_inconnu", BotanicalFamily.SOLANACEAE), 0f)
    }

    @Test
    fun verdictFlipsToTheMoreLikelyHostCause() {
        // Sans plante, la piéride l'emporte de peu ; sur une pomme de terre, c'est le doryphore.
        val p = probs("pieride_chou" to 0.42f, "doryphore" to 0.40f, "autre" to 0.18f)
        val none = PhotoDiagnosis.interpret(p, spec) as PhotoVerdict.Unsure
        assertEquals("pieride_chou", none.leads.first().first)
        val onPotato = PhotoDiagnosis.interpret(p, spec, BotanicalFamily.SOLANACEAE)
        assertTrue(onPotato is PhotoVerdict.Disease && onPotato.catalogId == "doryphore")
    }

    @Test
    fun priorKeepsTheModelThresholdUnchanged() {
        assertEquals(0.65f, spec.threshold, 0f)
    }

    @Test
    fun mergedLeafSpotClassOffersBothSheetsTogether() {
        val merged = PhotoModelSpec(
            1, PhotoModelSpec.Input(224, 224),
            listOf(cls("sain", null), cls("tache_feuille", null, listOf("mildiou", "alternariose")), cls("autre", null)),
            0.65f,
        )
        val verdict = PhotoDiagnosis.interpret(floatArrayOf(0.05f, 0.9f, 0.05f), merged) as PhotoVerdict.Unsure
        assertEquals(setOf("mildiou", "alternariose"), verdict.leads.map { it.first }.toSet())
        assertEquals(0.9f, verdict.leads.sumOf { it.second.toDouble() }.toFloat(), 1e-5f)
    }
}
