package fr.pilmil.sillon.engine

import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.data.VegetableCatalog
import fr.pilmil.sillon.model.DiagnosticJournalEntry
import fr.pilmil.sillon.model.Disease
import fr.pilmil.sillon.model.FollowUpAnswer
import fr.pilmil.sillon.model.Garden
import fr.pilmil.sillon.model.JournalDraft
import fr.pilmil.sillon.model.JournalStatus
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.PathogenType
import fr.pilmil.sillon.model.plusDays
import kotlinx.datetime.LocalDate

/**
 * Journal des problèmes d'un carré : création, suivi et clôture des entrées, sans effet de bord.
 * Toutes les fonctions rendent de nouvelles listes ou un nouveau potager.
 */
object DiagnosticJournal {

    const val MAX_ACTION_CHARS = 200
    const val MAX_ENTRIES = 1000

    /** Intervalles proposés pour « Revérifier dans N jours ». */
    val FOLLOW_UP_CHOICES: List<Int> = listOf(2, 3, 5, 7, 10, 14)

    private const val DAYS_PEST = 3
    private const val DAYS_PHYSIOLOGICAL = 3
    private const val DAYS_DISEASE = 7
    private const val DAYS_UNCERTAIN = 5

    /**
     * Intervalle proposé d'office. Ce sont des choix éditoriaux, pas des mesures : un ravageur ou un trouble de
     * culture se juge vite, une maladie du feuillage demande une semaine, une cause incertaine cinq jours.
     * La personne peut toujours changer la valeur.
     */
    fun defaultFollowUpDays(disease: Disease?): Int = when (disease?.pathogenType) {
        null -> DAYS_UNCERTAIN
        PathogenType.PEST -> DAYS_PEST
        PathogenType.PHYSIOLOGICAL -> DAYS_PHYSIOLOGICAL
        PathogenType.FUNGUS, PathogenType.BACTERIA, PathogenType.VIRUS -> DAYS_DISEASE
    }

    /**
     * Ajoute une entrée pour la plante actuellement dans la case. Sans plante dans la case, ou avec un
     * identifiant déjà pris, le potager est rendu tel quel. Au-delà de [MAX_ENTRIES], les plus anciennes
     * entrées closes sont retirées d'abord.
     */
    fun add(garden: Garden, draft: JournalDraft, today: LocalDate): Garden {
        if (garden.diagnosticJournal.any { it.id == draft.id }) return garden
        val slot = garden.parcels.firstOrNull { it.id == draft.parcelId }?.getSlot(draft.row, draft.col)
        val vegetableId = slot?.vegetableId ?: return garden
        val entry = DiagnosticJournalEntry(
            id = draft.id,
            parcelId = draft.parcelId,
            row = draft.row,
            col = draft.col,
            vegetableId = vegetableId,
            plantedDate = slot.plantedDate,
            diseaseId = draft.diseaseId,
            noticedOn = today,
            action = draft.action?.trim()?.take(MAX_ACTION_CHARS)?.takeIf { it.isNotEmpty() },
        )
        return garden.copy(diagnosticJournal = trimmed(garden.diagnosticJournal + entry))
    }

    private fun trimmed(entries: List<DiagnosticJournalEntry>): List<DiagnosticJournalEntry> {
        var excess = entries.size - MAX_ENTRIES
        if (excess <= 0) return entries
        val dropped = entries.filter { !it.isOpen }.sortedBy { it.noticedOn }.take(excess).map { it.id }.toSet()
        excess -= dropped.size
        return entries.filter { it.id !in dropped }.let { if (excess > 0) it.drop(excess) else it }
    }

    /** Fixe la revérification dans [days] jours, ou la retire si [days] est null. Sans effet sur une entrée close. */
    fun setFollowUp(entries: List<DiagnosticJournalEntry>, id: String, days: Int?, today: LocalDate): List<DiagnosticJournalEntry> =
        entries.map { entry ->
            if (entry.id != id || !entry.isOpen) {
                entry
            } else if (days == null) {
                entry.copy(followUpOn = null, followUpDays = null)
            } else {
                val clamped = days.coerceIn(1, 60)
                entry.copy(followUpOn = today.plusDays(clamped.toLong()), followUpDays = clamped)
            }
        }

    /**
     * Applique la réponse à « Ça va mieux ? ». Oui et « perdu » closent l'entrée. Non la prolonge : elle reste
     * ouverte et la revérification est reprogrammée avec le même intervalle (ou celui du problème, à défaut).
     */
    fun answer(entries: List<DiagnosticJournalEntry>, id: String, answer: FollowUpAnswer, today: LocalDate): List<DiagnosticJournalEntry> =
        entries.map { entry ->
            if (entry.id != id || !entry.isOpen) {
                entry
            } else {
                when (answer) {
                    FollowUpAnswer.BETTER -> close(entry, JournalStatus.BETTER, today)
                    FollowUpAnswer.LOST -> close(entry, JournalStatus.LOST, today)
                    FollowUpAnswer.NOT_BETTER -> {
                        val days = entry.followUpDays
                            ?: defaultFollowUpDays(entry.diseaseId?.let(DiseaseCatalog::getById))
                        entry.copy(followUpOn = today.plusDays(days.toLong()), followUpDays = days)
                    }
                }
            }
        }

    private fun close(entry: DiagnosticJournalEntry, status: JournalStatus, today: LocalDate) =
        entry.copy(status = status, closedOn = today, followUpOn = null)

    /** La case est vidée ou replantée : ses entrées ouvertes se closent sans verdict. */
    fun endForSlot(entries: List<DiagnosticJournalEntry>, parcelId: String, row: Int, col: Int, today: LocalDate): List<DiagnosticJournalEntry> =
        entries.map { entry ->
            if (entry.isOpen && entry.parcelId == parcelId && entry.row == row && entry.col == col) {
                close(entry, JournalStatus.ENDED, today)
            } else {
                entry
            }
        }

    /** Une parcelle supprimée emporte son journal. */
    fun forgetParcel(entries: List<DiagnosticJournalEntry>, parcelId: String): List<DiagnosticJournalEntry> =
        entries.filterNot { it.parcelId == parcelId }

    /** Entrées d'un emplacement, les plus récentes d'abord. */
    fun forSlot(entries: List<DiagnosticJournalEntry>, parcelId: String, row: Int, col: Int): List<DiagnosticJournalEntry> =
        entries.filter { it.parcelId == parcelId && it.row == row && it.col == col }
            .sortedByDescending { it.noticedOn }

    /** Entrées ouvertes dont la revérification est arrivée à échéance, en excluant les plantes déjà remplacées. */
    fun due(garden: Garden, today: LocalDate): List<DiagnosticJournalEntry> =
        garden.diagnosticJournal.filter { entry ->
            entry.isDue(today) &&
                entry.concerns(garden.parcels.firstOrNull { it.id == entry.parcelId }?.getSlot(entry.row, entry.col))
        }.sortedBy { it.followUpOn }

    /**
     * Cases de [parcel] dont la plante a un problème ouvert : c'est l'état « abîmé » de la scène.
     * Une entrée close, ou qui se rapporte à une autre culture que celle de la case, n'abîme rien.
     */
    fun damagedCells(parcel: Parcel, entries: List<DiagnosticJournalEntry>): Set<Pair<Int, Int>> =
        entries.asSequence()
            .filter { it.isOpen && it.parcelId == parcel.id && it.concerns(parcel.getSlot(it.row, it.col)) }
            .map { it.row to it.col }
            .toSet()

    /** Nom du légume d'une entrée, pour l'affichage et les rappels. */
    fun vegetableName(entry: DiagnosticJournalEntry): String =
        VegetableCatalog.getById(entry.vegetableId)?.name ?: entry.vegetableId
}
