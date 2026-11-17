package fr.pilmil.sillon

import fr.pilmil.sillon.data.GardenCodec
import fr.pilmil.sillon.data.GardenValidationException
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.model.CropHistoryEntry
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.model.Garden
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelSlot
import fr.pilmil.sillon.model.ParcelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GardenCodecTest {

    private val now = 1_750_000_000_000L

    private fun baseGarden(
        parcels: List<Parcel> = listOf(
            Parcel("p1", "Carré", ParcelType.RAISED_BED_SQUARES, 2, 2),
        ),
        growLightHoursPerDay: Int? = null,
    ) = Garden(
        id = "g1",
        name = "Test",
        frostDate = FrostDate(5, 15),
        parcels = parcels,
        growLightHoursPerDay = growLightHoursPerDay,
    )

    @Test
    fun encodeAndDecodeAndValidateRoundtrip() {
        val garden = baseGarden(
            parcels = listOf(
                Parcel(
                    id = "p1",
                    name = "Carré",
                    type = ParcelType.CONTAINER,
                    rows = 2,
                    cols = 2,
                    slots = listOf(ParcelSlot(1, 1, "tomate", now - 10_000L)),
                    containerVolumeLiters = 30,
                ),
            ),
            growLightHoursPerDay = 12,
        )
        val encoded = GardenCodec.encode(garden)
        val decoded = GardenCodec.decodeAndValidate(encoded, now = now)
        assertEquals(garden, decoded)
    }

    @Test
    fun rejectsOversizedFile() {
        val huge = " ".repeat(GardenCodec.MAX_FILE_CHARS + 1)
        assertFailsWith<GardenValidationException> {
            GardenCodec.decodeAndValidate(huge, now = now)
        }
    }

    @Test
    fun rejectsTooManyParcelsAndDuplicateParcelIds() {
        val tooMany = (1..(GardenCodec.MAX_PARCELS + 1)).map {
            Parcel("p$it", "P$it", ParcelType.ROW_BED, 1, 1)
        }
        assertFailsWith<GardenValidationException> {
            GardenCodec.validate(baseGarden(parcels = tooMany), now = now)
        }

        val duplicateIds = listOf(
            Parcel("dup", "A", ParcelType.ROW_BED, 1, 1),
            Parcel("dup", "B", ParcelType.ROW_BED, 1, 1),
        )
        assertFailsWith<GardenValidationException> {
            GardenCodec.validate(baseGarden(parcels = duplicateIds), now = now)
        }
    }

    @Test
    fun rejectsInvalidGrowLightHoursAndContainerVolume() {
        assertFailsWith<GardenValidationException> {
            GardenCodec.validate(baseGarden(growLightHoursPerDay = 0), now = now)
        }
        assertFailsWith<GardenValidationException> {
            GardenCodec.validate(baseGarden(growLightHoursPerDay = 25), now = now)
        }
        assertFailsWith<GardenValidationException> {
            GardenCodec.validate(
                baseGarden(parcels = listOf(Parcel("p1", "Pot", ParcelType.CONTAINER, 1, 1, containerVolumeLiters = 0))),
                now = now,
            )
        }
        assertFailsWith<GardenValidationException> {
            GardenCodec.validate(
                baseGarden(parcels = listOf(Parcel("p1", "Pot", ParcelType.CONTAINER, 1, 1, containerVolumeLiters = 2001))),
                now = now,
            )
        }
    }

    @Test
    fun rejectsOutOfBoundsAndDuplicateSlots() {
        assertFailsWith<GardenValidationException> {
            GardenCodec.validate(
                baseGarden(
                    parcels = listOf(
                        Parcel("p1", "Carré", ParcelType.RAISED_BED_SQUARES, 2, 2, listOf(ParcelSlot(2, 0, "radis"))),
                    ),
                ),
                now = now,
            )
        }
        assertFailsWith<GardenValidationException> {
            GardenCodec.validate(
                baseGarden(
                    parcels = listOf(
                        Parcel("p1", "Carré", ParcelType.RAISED_BED_SQUARES, 2, 2, listOf(ParcelSlot(0, -1, "radis"))),
                    ),
                ),
                now = now,
            )
        }
        assertFailsWith<GardenValidationException> {
            GardenCodec.validate(
                baseGarden(
                    parcels = listOf(
                        Parcel(
                            "p1",
                            "Carré",
                            ParcelType.RAISED_BED_SQUARES,
                            2,
                            2,
                            listOf(ParcelSlot(0, 0, "radis"), ParcelSlot(0, 0, "carotte")),
                        ),
                    ),
                ),
                now = now,
            )
        }
    }

    @Test
    fun oldOeilletDIndeIdIsMappedToTageteOnRead() {
        val garden = baseGarden(
            parcels = listOf(
                Parcel(
                    "p1", "Carré", ParcelType.RAISED_BED_SQUARES, 2, 2,
                    listOf(ParcelSlot(0, 0, "tagete", plantedDate = 1_700_000_000_000L), ParcelSlot(0, 1, "tomate")),
                ),
            ),
        ).copy(history = listOf(CropHistoryEntry("p1", 0, 0, 2025, BotanicalFamily.ASTERACEAE, "tagete")))
        // Fichier enregistré avant la fusion : l'ancien identifiant figure dans les cases et dans l'historique.
        val legacyJson = GardenCodec.encode(garden).replace("\"tagete\"", "\"oeillet_d_inde\"")
        assertTrue(legacyJson.contains("oeillet_d_inde"))

        val decoded = GardenCodec.decode(legacyJson)
        assertEquals("tagete", decoded.parcels.single().slots[0].vegetableId)
        assertEquals(1_700_000_000_000L, decoded.parcels.single().slots[0].plantedDate)
        assertEquals("tomate", decoded.parcels.single().slots[1].vegetableId)
        assertEquals("tagete", decoded.history.single().vegetableId)

        // Import (JSON ou .pilmil, une fois déchiffré) : la validation du catalogue accepte le fichier.
        val imported = GardenCodec.decodeAndValidate(legacyJson, now = now)
        assertEquals("tagete", imported.parcels.single().slots[0].vegetableId)
    }

    @Test
    fun currentFileIsUnchangedByLegacyMapping() {
        val garden = baseGarden(
            parcels = listOf(
                Parcel("p1", "Carré", ParcelType.RAISED_BED_SQUARES, 2, 2, listOf(ParcelSlot(0, 0, "tagete"))),
            ),
        )
        assertEquals(garden, GardenCodec.decode(GardenCodec.encode(garden)))
    }

    @Test
    fun unknownVegetableIdIsStillRefusedOnImport() {
        val garden = baseGarden(
            parcels = listOf(
                Parcel("p1", "Carré", ParcelType.RAISED_BED_SQUARES, 2, 2, listOf(ParcelSlot(0, 0, "tagete"))),
            ),
        )
        val json = GardenCodec.encode(garden).replace("\"tagete\"", "\"plante_inconnue\"")
        assertFailsWith<GardenValidationException> { GardenCodec.decodeAndValidate(json, now = now) }
        assertFalse(GardenCodec.decode(json).parcels.single().slots.single().vegetableId == "tagete")
    }
}
