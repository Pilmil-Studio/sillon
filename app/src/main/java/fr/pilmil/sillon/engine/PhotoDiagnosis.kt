package fr.pilmil.sillon.engine

import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.data.HostPlausibility
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.model.PlantOrgan
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Description du modèle embarqué, lue dans assets/models/sillon_disease.json
 * (produit par tools/ml/train_sillon.py, à côté de sillon_disease.tflite).
 */
@Serializable
data class PhotoModelSpec(
    val format: Int,
    val input: Input,
    val classes: List<ModelClass>,
    val threshold: Float,
    val synthetic: Boolean = false,
) {
    @Serializable
    data class Input(val width: Int, val height: Int, val channels: Int = 3, val order: String = "RGB")

    @Serializable
    data class ModelClass(
        val label: String,
        val catalogId: String? = null,
        /** Modèle fusionné : classes de base regroupées (ex. mildiou et alternariose dans « tache_feuille »). */
        @SerialName("group_members") val groupMembers: List<String>? = null,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): PhotoModelSpec = json.decodeFromString(serializer(), text)
    }
}

/** Ce que l'appli peut dire honnêtement d'une photo. */
sealed interface PhotoVerdict {
    /** Une maladie connue du catalogue, avec une confiance au-dessus du seuil. */
    data class Disease(val catalogId: String, val confidence: Float) : PhotoVerdict

    /** Feuille saine, avec une confiance au-dessus du seuil. */
    data class Healthy(val confidence: Float) : PhotoVerdict

    /**
     * Pas assez sûr, ou un problème que le modèle ne sait pas nommer :
     * on renvoie vers la clé de diagnostic, avec les pistes les plus probables s'il y en a.
     */
    data class Unsure(val leads: List<Pair<String, Float>>) : PhotoVerdict
}

/** État du substrat à 2–3 cm de profondeur pour départager les stress hydriques qu'une photo seule ne distingue pas. */
enum class SubstrateMoisture(val frenchLabel: String) {
    DRY("Terreau sec"),
    MOIST("Terreau frais"),
    WATERLOGGED("Terreau détrempé"),
}

/** Indices visuels complémentaires (port de la plante, environnement du pot). */
enum class AbioticSymptomHint(val frenchLabel: String) {
    WILTED_OR_DROOPING("Feuilles molles / tombantes"),
    STRETCHED_PALE_STEMS("Tiges longues et pâles vers la fenêtre"),
    TINY_WEBS_OR_SPECKS("Feuilles piquetées / fines toiles"),
    GNATS_FROM_SOIL("Moucherons noirs sur le terreau"),
}

/** Confiance dite en mots : on n'affiche jamais le score brut du modèle. */
enum class ConfidenceTier { PROBABLE, POSSIBLE, UNSURE }

object PhotoDiagnosis {

    const val HEALTHY_LABEL = "sain"
    private const val MIN_LEAD = 0.20f

    /**
     * Pixels ARGB d'une image déjà mise à la taille du modèle -> tenseur NHWC float RGB 0..255,
     * exactement ce que le script d'entraînement fournit au modèle.
     */
    fun toInput(argb: IntArray, width: Int, height: Int): FloatArray {
        require(argb.size == width * height) { "taille de pixels incohérente" }
        val out = FloatArray(width * height * 3)
        for (i in argb.indices) {
            val p = argb[i]
            out[i * 3] = ((p shr 16) and 0xFF).toFloat()
            out[i * 3 + 1] = ((p shr 8) and 0xFF).toFloat()
            out[i * 3 + 2] = (p and 0xFF).toFloat()
        }
        return out
    }

    /**
     * Score -> palier : « probable » dès le seuil du modèle (celui qui sépare un verdict d'une piste),
     * « possible » à partir du plancher des pistes, « je ne sais pas » en dessous. Bornes comprises.
     */
    fun confidenceTier(score: Float, threshold: Float): ConfidenceTier = when {
        score >= threshold -> ConfidenceTier.PROBABLE
        score >= MIN_LEAD -> ConfidenceTier.POSSIBLE
        else -> ConfidenceTier.UNSURE
    }

    /**
     * A priori de la plante : quand le diagnostic part d'une plantation, chaque probabilité d'une maladie ou
     * d'un ravageur est multipliée par la plausibilité de cet hôte ([HostPlausibility], jamais nulle), puis le
     * vecteur est renormalisé. Les classes sans fiche (sain, autre, hors_sujet, groupes fusionnés) ne sont pas
     * pondérées. Sans [family] (écran docteur autonome), les probabilités sont rendues telles quelles.
     */
    fun applyHostPrior(probabilities: FloatArray, spec: PhotoModelSpec, family: BotanicalFamily?): FloatArray {
        require(probabilities.size == spec.classes.size) { "le modèle et sa description ne correspondent pas" }
        if (family == null) return probabilities
        val weighted = FloatArray(probabilities.size) { i ->
            val id = spec.classes[i].catalogId
            val weight = if (id == null || spec.classes[i].label == HEALTHY_LABEL) 1f else HostPlausibility.plausibility(id, family)
            probabilities[i] * weight
        }
        val total = weighted.sum()
        if (total <= 0f) return probabilities
        return FloatArray(weighted.size) { weighted[it] / total }
    }

    /**
     * Probabilités du modèle -> verdict, avec le seuil calibré sur des photos de terrain. Le seuil n'est pas
     * recalibré pour l'a priori de [family] : il reste celui du modèle (voir specs/modele-diagnostic-ia.md).
     */
    fun interpret(rawProbabilities: FloatArray, spec: PhotoModelSpec, family: BotanicalFamily? = null): PhotoVerdict {
        require(rawProbabilities.size == spec.classes.size) { "le modèle et sa description ne correspondent pas" }
        val probabilities = applyHostPrior(rawProbabilities, spec, family)
        val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
        val best = ranked.first()
        val confidence = probabilities[best]
        val bestClass = spec.classes[best]
        // Classe fusionnée : jamais un verdict unique, les fiches du groupe sont proposées ensemble.
        bestClass.groupMembers?.takeIf { it.isNotEmpty() }?.let { members ->
            return PhotoVerdict.Unsure(members.map { it to confidence / members.size })
        }
        if (confidence >= spec.threshold) {
            if (bestClass.label == HEALTHY_LABEL) return PhotoVerdict.Healthy(confidence)
            bestClass.catalogId?.let { return PhotoVerdict.Disease(it, confidence) }
        }
        val leads = ranked
            .filter { probabilities[it] >= MIN_LEAD }
            .mapNotNull { i -> spec.classes[i].catalogId?.let { it to probabilities[i] } }
        return PhotoVerdict.Unsure(leads)
    }

    /**
     * Oriente vers les fiches de troubles abiotiques ou ravageurs d'intérieur lorsque la photo seule
     * ne permet pas de trancher (ex. flétrissement par manque d'eau vs asphyxie racinaire, étiolement).
     */
    fun resolveAbioticTriage(
        moisture: SubstrateMoisture?,
        hint: AbioticSymptomHint?,
    ): List<String> {
        val suggestions = linkedSetOf<String>()
        when (hint) {
            AbioticSymptomHint.STRETCHED_PALE_STEMS -> suggestions.add("etiolement")
            AbioticSymptomHint.GNATS_FROM_SOIL -> {
                suggestions.add("moucherons_terreau")
                suggestions.add("exces_eau_racines")
            }
            AbioticSymptomHint.TINY_WEBS_OR_SPECKS -> suggestions.add("tetranyques")
            AbioticSymptomHint.WILTED_OR_DROOPING -> when (moisture) {
                SubstrateMoisture.DRY -> suggestions.add("manque_eau")
                SubstrateMoisture.WATERLOGGED -> suggestions.add("exces_eau_racines")
                SubstrateMoisture.MOIST, null -> {
                    suggestions.add("manque_eau")
                    suggestions.add("exces_eau_racines")
                }
            }
            null -> Unit
        }
        when (moisture) {
            SubstrateMoisture.WATERLOGGED -> {
                suggestions.add("exces_eau_racines")
                suggestions.add("moucherons_terreau")
            }
            SubstrateMoisture.DRY -> {
                suggestions.add("manque_eau")
            }
            SubstrateMoisture.MOIST, null -> Unit
        }
        return suggestions.toList()
    }

    /** Miroir horizontal d'une image ARGB déjà mise à la taille cible, pour TTA 2 vues sans réallocation Bitmap. */
    fun flipHorizontal(argb: IntArray, width: Int, height: Int): IntArray {
        require(argb.size == width * height) { "taille de pixels incohérente" }
        val out = IntArray(argb.size)
        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                out[rowStart + (width - 1 - x)] = argb[rowStart + x]
            }
        }
        return out
    }

    /** Moyenne les vecteurs de probabilités de plusieurs vues (ex. original + miroir horizontal). */
    fun averageProbabilities(views: List<FloatArray>): FloatArray {
        require(views.isNotEmpty()) { "aucune vue à moyenner" }
        val size = views.first().size
        val out = FloatArray(size)
        for (view in views) {
            require(view.size == size) { "dimensions de probabilités incohérentes" }
            for (i in 0 until size) out[i] += view[i]
        }
        val inv = 1f / views.size.toFloat()
        for (i in 0 until size) out[i] *= inv
        return out
    }

    /**
     * Lorsque mildiou et alternariose apparaissent ensemble en pistes principales
     * et dépassent 55 % cumulés, signale une tache foliaire probable à départager.
     */
    fun leafSpotCombinedConfidence(leads: List<Pair<String, Float>>): Float? {
        val mildiou = leads.firstOrNull { it.first == "mildiou" }?.second ?: return null
        val alternariose = leads.firstOrNull { it.first == "alternariose" }?.second ?: return null
        val combined = mildiou + alternariose
        return if (combined >= 0.55f) combined else null
    }
}

/** Question posée pour départager mildiou et alternariose. */
enum class LeafSpotQuestion { WHITE_FUZZ_OR_OILY, CONCENTRIC_RINGS }

enum class TriageAnswer { YES, NO, UNKNOWN }

/**
 * Départage mildiou et alternariose, que la photo distingue mal, avec deux questions sur des signes visibles.
 *
 * Mise à jour simple : un « oui » multiplie par [YES_FACTOR] le poids de la maladie que le signe évoque, un « non »
 * le divise par [NO_FACTOR], « je ne sais pas » ne change rien ; la masse cumulée des deux maladies est conservée.
 * Ces facteurs sont des choix éditoriaux, pas des valeurs mesurées : ils ne servent qu'à ordonner les deux pistes,
 * jamais à afficher un chiffre. Le signe évoque la maladie dont la fiche du catalogue le décrit
 * (voir [target], qui s'appuie sur [DiagnosticEngine]).
 */
object LeafSpotTriage {

    const val MILDIOU = "mildiou"
    const val ALTERNARIOSE = "alternariose"

    /** Écart de probabilité en dessous duquel les deux premières pistes sont jugées trop proches. */
    const val CLOSE_GAP = 0.15f
    const val YES_FACTOR = 3f
    const val NO_FACTOR = 1.5f

    private val clues = mapOf(
        LeafSpotQuestion.WHITE_FUZZ_OR_OILY to "duvet blanc grisâtre au revers, taches d'aspect huileux",
        LeafSpotQuestion.CONCENTRIC_RINGS to "taches brunes à anneaux concentriques en cible",
    )

    /** Vrai si mildiou et alternariose sont les deux premières pistes avec un écart inférieur à [CLOSE_GAP]. */
    fun needsTriage(leads: List<Pair<String, Float>>): Boolean {
        val top = leads.sortedByDescending { it.second }.take(2)
        if (top.size < 2) return false
        return top.map { it.first }.toSet() == setOf(MILDIOU, ALTERNARIOSE) && top[0].second - top[1].second < CLOSE_GAP
    }

    /** Maladie évoquée par le signe de [question], d'après les symptômes des fiches du catalogue. */
    fun target(question: LeafSpotQuestion, engine: DiagnosticEngine = DiagnosticEngine()): String? =
        engine.diagnose(
            organ = PlantOrgan.LEAF,
            symptoms = listOf(clues.getValue(question)),
            family = BotanicalFamily.SOLANACEAE,
            catalog = listOfNotNull(DiseaseCatalog.getById(MILDIOU), DiseaseCatalog.getById(ALTERNARIOSE)),
        ).firstOrNull()?.disease?.id

    /** Probabilités de mildiou et d'alternariose après les réponses, avec la même masse cumulée qu'avant. */
    fun combine(mildiou: Float, alternariose: Float, answers: Map<LeafSpotQuestion, TriageAnswer>): Pair<Float, Float> {
        var wMildiou = mildiou
        var wAlternariose = alternariose
        for ((question, answer) in answers) {
            val factor = when (answer) {
                TriageAnswer.YES -> YES_FACTOR
                TriageAnswer.NO -> 1f / NO_FACTOR
                TriageAnswer.UNKNOWN -> 1f
            }
            when (target(question)) {
                MILDIOU -> wMildiou *= factor
                ALTERNARIOSE -> wAlternariose *= factor
            }
        }
        val mass = mildiou + alternariose
        val total = wMildiou + wAlternariose
        if (total <= 0f) return mildiou to alternariose
        return wMildiou / total * mass to wAlternariose / total * mass
    }

    /** Pistes mises à jour par les réponses, de la plus à la moins probable ; les autres pistes ne changent pas. */
    fun updatedLeads(leads: List<Pair<String, Float>>, answers: Map<LeafSpotQuestion, TriageAnswer>): List<Pair<String, Float>> {
        val mildiou = leads.firstOrNull { it.first == MILDIOU }?.second ?: return leads
        val alternariose = leads.firstOrNull { it.first == ALTERNARIOSE }?.second ?: return leads
        val (m, a) = combine(mildiou, alternariose, answers)
        return leads.map {
            when (it.first) {
                MILDIOU -> it.first to m
                ALTERNARIOSE -> it.first to a
                else -> it
            }
        }.sortedByDescending { it.second }
    }
}
