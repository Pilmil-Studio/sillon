package fr.pilmil.sillon

import fr.pilmil.sillon.data.GardenCodec
import fr.pilmil.sillon.data.GardenValidationException
import fr.pilmil.sillon.data.GardenVersionException
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.model.Garden
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelSlot
import fr.pilmil.sillon.model.ParcelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

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
    fun customFrostFieldsRoundtripAndStayWhenSwitchingBack() {
        val custom = baseGarden().copy(customFrostDate = FrostDate(6, 10), useCustomFrost = true)
        val decoded = GardenCodec.decodeAndValidate(GardenCodec.encode(custom), now = now)
        assertEquals(custom, decoded)
        assertEquals(FrostDate(6, 10), decoded.activeFrostDate)

        val back = custom.copy(useCustomFrost = false)
        val decodedBack = GardenCodec.decodeAndValidate(GardenCodec.encode(back), now = now)
        assertEquals(FrostDate(6, 10), decodedBack.customFrostDate)
        assertEquals(FrostDate(5, 15), decodedBack.activeFrostDate)
    }

    @Test
    fun versionOneFileIsReadInDepartmentMode() {
        val v1 = """{ "schemaVersion": 1, "id": "g", "name": "Ancien", "frostDate": { "month": 4, "day": 20 } }"""
        val garden = GardenCodec.decode(v1)
        assertFalse(garden.useCustomFrost)
        assertNull(garden.customFrostDate)
        assertEquals(FrostDate(4, 20), garden.activeFrostDate)
    }

    @Test
    fun versionOneFileCannotCarryCustomFrost() {
        val v1 = """{ "schemaVersion": 1, "id": "g", "name": "Ancien", "frostDate": { "month": 4, "day": 20 },
            "customFrostDate": { "month": 6, "day": 1 }, "useCustomFrost": true }"""
        val garden = GardenCodec.decode(v1)
        assertFalse(garden.useCustomFrost)
        assertNull(garden.customFrostDate)
    }

    @Test
    fun fileWithoutSchemaVersionIsReadInDepartmentMode() {
        val legacy = """{ "id": "g", "name": "Sans version", "frostDate": { "month": 5, "day": 1 } }"""
        assertFalse(GardenCodec.decode(legacy).useCustomFrost)
    }

    @Test
    fun newerVersionIsRefusedByDecodeAndByImport() {
        val future = """{ "schemaVersion": ${GardenCodec.SCHEMA_VERSION + 1}, "id": "g", "name": "F", "frostDate": { "month": 5, "day": 1 } }"""
        assertFailsWith<GardenVersionException> { GardenCodec.decode(future) }
        assertFailsWith<GardenValidationException> { GardenCodec.decodeAndValidate(future, now = now) }
    }

    @Test
    fun rejectsCustomFrostDateThatDoesNotExist() {
        val bad = baseGarden().copy(customFrostDate = FrostDate(4, 31), useCustomFrost = true)
        assertFailsWith<GardenValidationException> { GardenCodec.validate(bad, now = now) }
        GardenCodec.validate(baseGarden().copy(customFrostDate = FrostDate(2, 29), useCustomFrost = true), now = now)
    }
}
