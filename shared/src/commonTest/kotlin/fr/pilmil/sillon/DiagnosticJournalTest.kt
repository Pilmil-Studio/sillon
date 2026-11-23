package fr.pilmil.sillon

import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.data.GardenStore
import fr.pilmil.sillon.data.GardenLoad
import fr.pilmil.sillon.data.PlatformGardenStorage
import fr.pilmil.sillon.engine.DiagnosticJournal
import fr.pilmil.sillon.model.DiagnosticJournalEntry
import fr.pilmil.sillon.model.FollowUpAnswer
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.model.Garden
import fr.pilmil.sillon.model.JournalDraft
import fr.pilmil.sillon.model.JournalStatus
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelSlot
import fr.pilmil.sillon.model.ParcelType
import fr.pilmil.sillon.model.todayLocalDate
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticJournalTest {

    private val today = LocalDate(2026, 6, 1)
    private val planted = 1_778_414_400_000L

    private fun garden(vegetableId: String? = "tomate", journal: List<DiagnosticJournalEntry> = emptyList()): Garden {
        val slots = listOfNotNull(vegetableId?.let { ParcelSlot(0, 1, it, planted) }, ParcelSlot(1, 1, "carotte", planted))
        return Garden(
            id = "g",
            name = "Test",
            frostDate = FrostDate(5, 1),
            parcels = listOf(Parcel("p1", "Carré", ParcelType.RAISED_BED_SQUARES, 3, 3, slots)),
            diagnosticJournal = journal,
        )
    }

    private fun draft(id: String = "d1", diseaseId: String? = "mildiou", action: String? = "Feuilles touchées retirées") =
        JournalDraft(id, "p1", 0, 1, diseaseId, action)

    @Test
    fun addCreatesAnOpenEntryTiedToThePlantInTheSlot() {
        val result = DiagnosticJournal.add(garden(), draft(), today)
        val entry = result.diagnosticJournal.single()
        assertEquals("tomate", entry.vegetableId)
        assertEquals(planted, entry.plantedDate)
        assertEquals("mildiou", entry.diseaseId)
        assertEquals(today, entry.noticedOn)
        assertEquals("Feuilles touchées retirées", entry.action)
        assertEquals(JournalStatus.OPEN, entry.status)
        assertNull(entry.followUpOn)
        assertNull(entry.closedOn)
    }

    @Test
    fun uncertainCauseIsKeptAsNull() {
        val entry = DiagnosticJournal.add(garden(), draft(diseaseId = null, action = "   "), today).diagnosticJournal.single()
        assertNull(entry.diseaseId)
        assertNull(entry.action)
    }

    @Test
    fun longActionIsCut() {
        val entry = DiagnosticJournal.add(garden(), draft(action = "x".repeat(500)), today).diagnosticJournal.single()
        assertEquals(DiagnosticJournal.MAX_ACTION_CHARS, entry.action!!.length)
    }

    @Test
    fun addDoesNothingOnAnEmptySlotOrUnknownParcelOrDuplicateId() {
        val empty = garden(vegetableId = null)
        assertTrue(DiagnosticJournal.add(empty, draft(), today).diagnosticJournal.isEmpty())
        assertTrue(DiagnosticJournal.add(garden(), draft().copy(parcelId = "inconnue"), today).diagnosticJournal.isEmpty())
        val once = DiagnosticJournal.add(garden(), draft(), today)
        assertEquals(1, DiagnosticJournal.add(once, draft(), today).diagnosticJournal.size)
    }

    @Test
    fun defaultFollowUpDependsOnTheKindOfProblem() {
        assertEquals(3, DiagnosticJournal.defaultFollowUpDays(DiseaseCatalog.DORYPHORE))
        assertEquals(3, DiagnosticJournal.defaultFollowUpDays(DiseaseCatalog.MANQUE_EAU))
        assertEquals(7, DiagnosticJournal.defaultFollowUpDays(DiseaseCatalog.MILDIOU))
        assertEquals(5, DiagnosticJournal.defaultFollowUpDays(null))
        assertTrue(DiagnosticJournal.FOLLOW_UP_CHOICES.containsAll(listOf(3, 5, 7)))
    }

    @Test
    fun followUpSetChangedAndRemoved() {
        val g = DiagnosticJournal.add(garden(), draft(), today)
        val set = DiagnosticJournal.setFollowUp(g.diagnosticJournal, "d1", 7, today).single()
        assertEquals(LocalDate(2026, 6, 8), set.followUpOn)
        assertEquals(7, set.followUpDays)
        val changed = DiagnosticJournal.setFollowUp(listOf(set), "d1", 3, today).single()
        assertEquals(LocalDate(2026, 6, 4), changed.followUpOn)
        val removed = DiagnosticJournal.setFollowUp(listOf(changed), "d1", null, today).single()
        assertNull(removed.followUpOn)
        assertNull(removed.followUpDays)
    }

    @Test
    fun betterClosesTheEntryAndStopsTheReminder() {
        val open = DiagnosticJournal.setFollowUp(DiagnosticJournal.add(garden(), draft(), today).diagnosticJournal, "d1", 7, today)
        val later = LocalDate(2026, 6, 9)
        val closed = DiagnosticJournal.answer(open, "d1", FollowUpAnswer.BETTER, later).single()
        assertEquals(JournalStatus.BETTER, closed.status)
        assertEquals(later, closed.closedOn)
        assertNull(closed.followUpOn)
        assertFalse(closed.isOpen)
    }

    @Test
    fun lostClosesTheEntry() {
        val open = DiagnosticJournal.add(garden(), draft(), today).diagnosticJournal
        val closed = DiagnosticJournal.answer(open, "d1", FollowUpAnswer.LOST, today).single()
        assertEquals(JournalStatus.LOST, closed.status)
        assertEquals(today, closed.closedOn)
    }

    @Test
    fun notBetterKeepsTheEntryOpenAndSchedulesAnotherCheck() {
        val open = DiagnosticJournal.setFollowUp(DiagnosticJournal.add(garden(), draft(), today).diagnosticJournal, "d1", 5, today)
        val later = LocalDate(2026, 6, 6)
        val extended = DiagnosticJournal.answer(open, "d1", FollowUpAnswer.NOT_BETTER, later).single()
        assertEquals(JournalStatus.OPEN, extended.status)
        assertEquals(LocalDate(2026, 6, 11), extended.followUpOn)
        assertNull(extended.closedOn)
    }

    @Test
    fun notBetterWithoutAPreviousIntervalUsesTheProblemDefault() {
        val open = DiagnosticJournal.add(garden(), draft(diseaseId = "doryphore"), today).diagnosticJournal
        val extended = DiagnosticJournal.answer(open, "d1", FollowUpAnswer.NOT_BETTER, today).single()
        assertEquals(LocalDate(2026, 6, 4), extended.followUpOn)
    }

    @Test
    fun aClosedEntryIsNotChangedByLaterCalls() {
        val closed = DiagnosticJournal.answer(DiagnosticJournal.add(garden(), draft(), today).diagnosticJournal, "d1", FollowUpAnswer.BETTER, today)
        assertEquals(closed, DiagnosticJournal.answer(closed, "d1", FollowUpAnswer.LOST, LocalDate(2026, 7, 1)))
        assertEquals(closed, DiagnosticJournal.setFollowUp(closed, "d1", 7, LocalDate(2026, 7, 1)))
    }

    @Test
    fun emptyingTheSlotEndsItsOpenEntriesOnly() {
        val g = DiagnosticJournal.add(DiagnosticJournal.add(garden(), draft("a"), today), draft("b").copy(row = 1), today)
        val ended = DiagnosticJournal.endForSlot(g.diagnosticJournal, "p1", 0, 1, today)
        assertEquals(JournalStatus.ENDED, ended.first { it.id == "a" }.status)
        assertEquals(JournalStatus.OPEN, ended.first { it.id == "b" }.status)
    }

    @Test
    fun dueEntriesAreOpenReachedAndStillAboutThePlantInTheSlot() {
        val g = DiagnosticJournal.add(garden(), draft(), today)
        val withFollowUp = g.copy(diagnosticJournal = DiagnosticJournal.setFollowUp(g.diagnosticJournal, "d1", 3, today))
        assertTrue(DiagnosticJournal.due(withFollowUp, LocalDate(2026, 6, 3)).isEmpty())
        assertEquals(1, DiagnosticJournal.due(withFollowUp, LocalDate(2026, 6, 4)).size)
        // La case est replantée avec la même plante, à une autre date : l'ancienne entrée ne se rapporte plus à elle.
        val replanted = withFollowUp.copy(
            parcels = withFollowUp.parcels.map { it.copy(slots = it.slots.map { s -> if (s.col == 1 && s.row == 0) s.copy(plantedDate = planted + 1) else s }) },
        )
        assertTrue(DiagnosticJournal.due(replanted, LocalDate(2026, 6, 10)).isEmpty())
    }

    // État « abîmé » de la scène : la logique qui décide quelles cases sont dessinées abîmées.

    @Test
    fun openProblemDamagesThePlantAndClosingItRestoresIt() {
        val g = DiagnosticJournal.add(garden(), draft(), today)
        val parcel = g.parcels.single()
        assertEquals(setOf(0 to 1), DiagnosticJournal.damagedCells(parcel, g.diagnosticJournal))
        val better = DiagnosticJournal.answer(g.diagnosticJournal, "d1", FollowUpAnswer.BETTER, today)
        assertTrue(DiagnosticJournal.damagedCells(parcel, better).isEmpty())
    }

    @Test
    fun lostOrEndedEntriesDoNotDamageAnything() {
        val g = DiagnosticJournal.add(garden(), draft(), today)
        val parcel = g.parcels.single()
        assertTrue(DiagnosticJournal.damagedCells(parcel, DiagnosticJournal.answer(g.diagnosticJournal, "d1", FollowUpAnswer.LOST, today)).isEmpty())
        assertTrue(DiagnosticJournal.damagedCells(parcel, DiagnosticJournal.endForSlot(g.diagnosticJournal, "p1", 0, 1, today)).isEmpty())
    }

    @Test
    fun anOpenEntryOfAnotherPlantDoesNotDamageTheNewOne() {
        val g = DiagnosticJournal.add(garden(), draft(), today)
        val parcel = g.parcels.single()
        val replaced = parcel.copy(slots = parcel.slots.map { if (it.row == 0 && it.col == 1) it.copy(vegetableId = "radis") else it })
        assertTrue(DiagnosticJournal.damagedCells(replaced, g.diagnosticJournal).isEmpty())
    }

    @Test
    fun damagedCellsOfOtherParcelsAreIgnored() {
        val g = DiagnosticJournal.add(garden(), draft(), today)
        val other = Parcel("p2", "Autre", ParcelType.RAISED_BED_SQUARES, 3, 3, listOf(ParcelSlot(0, 1, "tomate", planted)))
        assertTrue(DiagnosticJournal.damagedCells(other, g.diagnosticJournal).isEmpty())
    }

    // Même scénarios de bout en bout par le magasin du potager (persistance atomique).

    private class MemoryStorage(var content: String? = null) : PlatformGardenStorage {
        override fun exists() = content != null
        override fun readText() = content
        override fun writeTextAtomic(content: String): Boolean { this.content = content; return true }
        override fun quarantineCorrupt() { content = null }
    }

    private fun storeWith(garden: Garden): GardenStore {
        val storage = MemoryStorage()
        val store = GardenStore(storage)
        store.saveGarden(garden)
        assertTrue(store.load() is GardenLoad.Ready)
        return store
    }

    @Test
    fun storeCreatesFollowsUpAndClosesAnEntry() {
        val store = storeWith(garden())
        val created = store.addJournalEntry(draft("s1"))
        assertTrue(created.saved)
        assertEquals(todayLocalDate(), created.garden.diagnosticJournal.single().noticedOn)
        val scheduled = store.setJournalFollowUp("s1", 7)
        assertEquals(7, scheduled.garden.diagnosticJournal.single().followUpDays)
        val closed = store.answerJournalEntry("s1", FollowUpAnswer.BETTER)
        assertEquals(JournalStatus.BETTER, closed.garden.diagnosticJournal.single().status)
    }

    @Test
    fun storeEndsEntriesWhenTheSlotIsEmptiedOrReplanted() {
        val store = storeWith(garden())
        store.addJournalEntry(draft("s1"))
        val emptied = store.updateSlot("p1", 0, 1, null)
        assertEquals(JournalStatus.ENDED, emptied.garden.diagnosticJournal.single().status)
        assertTrue(emptied.garden.parcels.single().slots.none { it.row == 0 && it.col == 1 })
    }

    @Test
    fun storeForgetsTheJournalOfADeletedParcelButNotOfOthers() {
        val base = garden()
        val second = Parcel("p2", "Second", ParcelType.RAISED_BED_SQUARES, 2, 2, listOf(ParcelSlot(0, 0, "radis", planted)))
        val store = storeWith(base.copy(parcels = base.parcels + second))
        store.addJournalEntry(draft("s1"))
        store.addJournalEntry(JournalDraft("s2", "p2", 0, 0, null, null))
        val after = store.deleteParcel("p1")
        assertEquals(listOf("s2"), after.garden.diagnosticJournal.map { it.id })
    }

    @Test
    fun storeKeepsExistingDataWhenNotingAProblem() {
        val base = garden()
        val store = storeWith(base)
        val after = store.addJournalEntry(draft("s1")).garden
        assertEquals(base.parcels, after.parcels)
        assertEquals(base.history, after.history)
        assertEquals(base.frostDate, after.frostDate)
    }
}
