package fr.pilmil.sillon

import fr.pilmil.sillon.data.GardenCodec
import fr.pilmil.sillon.data.GardenValidationException
import fr.pilmil.sillon.model.DiagnosticJournalEntry
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.model.Garden
import fr.pilmil.sillon.model.JournalStatus
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelSlot
import fr.pilmil.sillon.model.ParcelType
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Schéma 3 : le journal des problèmes s'ajoute sans rien retirer aux potagers existants. */
class GardenJournalMigrationTest {

    private val now = 1_790_000_000_000L

    private val entry = DiagnosticJournalEntry(
        id = "d1",
        parcelId = "p1",
        row = 0,
        col = 0,
        vegetableId = "tomate",
        plantedDate = 1_778_414_400_000L,
        diseaseId = "mildiou",
        noticedOn = LocalDate(2026, 6, 1),
        action = "Feuilles retirées",
        followUpOn = LocalDate(2026, 6, 8),
        followUpDays = 7,
    )

    private fun garden(journal: List<DiagnosticJournalEntry> = emptyList()) = Garden(
        id = "g",
        name = "Mon potager",
        frostDate = FrostDate(5, 1),
        parcels = listOf(Parcel("p1", "Carré", ParcelType.RAISED_BED_SQUARES, 2, 2, listOf(ParcelSlot(0, 0, "tomate", 1_778_414_400_000L)))),
        diagnosticJournal = journal,
    )

    private val v2File = """
        {
          "schemaVersion": 2,
          "id": "g", "name": "Ancien potager", "frostDate": { "month": 4, "day": 20 },
          "customFrostDate": { "month": 3, "day": 15 }, "useCustomFrost": true,
          "parcels": [ { "id": "p1", "name": "Carré", "type": "RAISED_BED_SQUARES", "rows": 2, "cols": 2,
                         "slots": [ { "row": 0, "col": 0, "vegetableId": "tomate", "plantedDate": 1778414400000 } ] } ],
          "history": [ { "parcelId": "p1", "row": 0, "col": 0, "year": 2025, "family": "SOLANACEAE", "vegetableId": "pomme_de_terre" } ]
        }
    """.trimIndent()

    @Test
    fun schemaVersionIsThree() {
        assertEquals(3, GardenCodec.SCHEMA_VERSION)
    }

    @Test
    fun aVersion2FileOpensWithAnEmptyJournalAndKeepsEverythingElse() {
        val garden = GardenCodec.decode(v2File)
        assertTrue(garden.diagnosticJournal.isEmpty())
        assertEquals("Ancien potager", garden.name)
        assertEquals(FrostDate(4, 20), garden.frostDate)
        assertEquals(FrostDate(3, 15), garden.customFrostDate)
        assertTrue(garden.useCustomFrost)
        assertEquals("tomate", garden.parcels.single().slots.single().vegetableId)
        assertEquals(2025, garden.history.single().year)
    }

    @Test
    fun aVersion1FileStillOpens() {
        val v1 = """{ "id": "g", "name": "Très ancien", "frostDate": { "month": 4, "day": 20 } }"""
        val garden = GardenCodec.decode(v1)
        assertEquals("Très ancien", garden.name)
        assertTrue(garden.diagnosticJournal.isEmpty())
    }

    @Test
    fun migratingTwiceOrAnUpToDateFileChangesNothing() {
        val root = buildJsonObject {
            put("schemaVersion", 3)
            put("id", "g")
            put("diagnosticJournal", kotlinx.serialization.json.JsonArray(emptyList()))
        }
        assertEquals(root, GardenCodec.migrate(root, 3))
        val old = GardenCodec.migrate(JsonObject(root - "schemaVersion"), 2)
        assertFalse("diagnosticJournal" in old)
        assertEquals(old, GardenCodec.migrate(old, 3))
    }

    @Test
    fun anOlderFileCannotCarryAJournal() {
        val stray = v2File.replaceFirst("\"id\": \"g\"", "\"diagnosticJournal\": [ { \"id\": \"x\" } ], \"id\": \"g\"")
        assertTrue(GardenCodec.decode(stray).diagnosticJournal.isEmpty())
    }

    @Test
    fun theJournalSurvivesAWriteAndReadAndIsWrittenInVersion3() {
        val written = GardenCodec.encode(garden(listOf(entry)))
        assertTrue(written.contains("\"schemaVersion\": 3"))
        assertEquals(listOf(entry), GardenCodec.decodeAndValidate(written, now).diagnosticJournal)
    }

    @Test
    fun noPhotoOrBinaryFieldIsStoredInTheJournal() {
        val keys = GardenCodec.encode(garden(listOf(entry))).let { text ->
            kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject["diagnosticJournal"].toString()
        }
        assertFalse(keys.contains("photo", ignoreCase = true))
        assertFalse(keys.contains("image", ignoreCase = true))
    }

    @Test
    fun closedStatusRoundTrips() {
        val closed = entry.copy(status = JournalStatus.BETTER, closedOn = LocalDate(2026, 6, 9), followUpOn = null)
        assertEquals(listOf(closed), GardenCodec.decodeAndValidate(GardenCodec.encode(garden(listOf(closed))), now).diagnosticJournal)
    }

    @Test
    fun importRefusesAnUnknownDiseaseOrPlant() {
        assertFailsWith<GardenValidationException> { GardenCodec.validate(garden(listOf(entry.copy(diseaseId = "inconnue"))), now) }
        assertFailsWith<GardenValidationException> { GardenCodec.validate(garden(listOf(entry.copy(vegetableId = "inconnue"))), now) }
    }

    @Test
    fun importRefusesDuplicateIdsAndOversizedFields() {
        assertFailsWith<GardenValidationException> { GardenCodec.validate(garden(listOf(entry, entry)), now) }
        assertFailsWith<GardenValidationException> { GardenCodec.validate(garden(listOf(entry.copy(action = "x".repeat(500)))), now) }
        assertFailsWith<GardenValidationException> { GardenCodec.validate(garden(listOf(entry.copy(followUpDays = 900))), now) }
    }

    @Test
    fun importAcceptsAnUncertainCause() {
        GardenCodec.validate(garden(listOf(entry.copy(diseaseId = null))), now)
    }
}
