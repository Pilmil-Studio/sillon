package fr.pilmil.sillon.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** État d'une entrée du journal des problèmes d'un carré. Seule [OPEN] est un problème en cours. */
@Serializable
enum class JournalStatus {
    /** Problème noté, pas encore réglé. */
    OPEN,

    /** « Ça va mieux » : l'entrée est close. */
    BETTER,

    /** « Le plant est perdu » : l'entrée est close. */
    LOST,

    /** L'emplacement a été vidé ou replanté : l'entrée est close sans verdict. */
    ENDED,
}

/** Réponse à « Ça va mieux ? » lors d'une revérification. */
enum class FollowUpAnswer { BETTER, NOT_BETTER, LOST }

/**
 * Un problème noté sur la plante d'un carré (une case d'une parcelle).
 *
 * La photo d'un diagnostic n'est jamais enregistrée : l'entrée ne garde que du texte et des dates.
 * [diseaseId] vaut null quand la cause est restée incertaine.
 * [plantedDate] est la date de mise en place de la culture au moment de la note : elle rattache l'entrée
 * à cette culture et non à celle qui viendrait la remplacer dans la même case.
 */
@Serializable
data class DiagnosticJournalEntry(
    val id: String,
    val parcelId: String,
    val row: Int,
    val col: Int,
    val vegetableId: String,
    val plantedDate: Long? = null,
    val diseaseId: String? = null,
    val noticedOn: LocalDate,
    /** Traitement ou action choisi, en quelques mots. */
    val action: String? = null,
    /** Jour de la prochaine revérification, ou null si la personne n'a pas voulu de rappel. */
    val followUpOn: LocalDate? = null,
    /** Intervalle choisi, repris quand la réponse est « Non » pour prolonger le suivi. */
    val followUpDays: Int? = null,
    val status: JournalStatus = JournalStatus.OPEN,
    val closedOn: LocalDate? = null,
) {
    val isOpen: Boolean get() = status == JournalStatus.OPEN

    /** Vrai si la case contient encore la culture à laquelle cette entrée se rapporte. */
    fun concerns(slot: ParcelSlot?): Boolean =
        slot != null && slot.vegetableId == vegetableId && slot.plantedDate == plantedDate

    fun isDue(today: LocalDate): Boolean = isOpen && followUpOn != null && followUpOn <= today
}

/** Ce que la personne a saisi pour noter un problème. */
data class JournalDraft(
    val id: String,
    val parcelId: String,
    val row: Int,
    val col: Int,
    val diseaseId: String?,
    val action: String?,
)
