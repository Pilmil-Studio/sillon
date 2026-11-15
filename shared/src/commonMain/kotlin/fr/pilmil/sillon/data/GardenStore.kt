package fr.pilmil.sillon.data

import androidx.compose.runtime.Composable
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.model.CropHistoryEntry
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.model.Garden
import fr.pilmil.sillon.model.GrowingEnvironment
import fr.pilmil.sillon.model.LightSource
import fr.pilmil.sillon.model.LocalWeather
import fr.pilmil.sillon.model.Orientation
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelSlot
import fr.pilmil.sillon.model.ParcelType
import fr.pilmil.sillon.model.WindExposure
import fr.pilmil.sillon.model.epochMillisToLocalDate
import fr.pilmil.sillon.model.nowEpochMillis
import fr.pilmil.sillon.model.todayLocalDate

data class GardenUpdate(val garden: Garden, val saved: Boolean)

interface PlatformGardenStorage {
    fun exists(): Boolean
    fun readText(): String?
    fun writeTextAtomic(content: String): Boolean
    fun quarantineCorrupt()
}

class GardenStore(private val storage: PlatformGardenStorage) {

    private var recoveryNotice = false

    fun consumeRecoveryNotice(): Boolean {
        val pending = recoveryNotice
        recoveryNotice = false
        return pending
    }

    fun loadGarden(): Garden {
        if (!storage.exists()) return createDefaultGarden().also { saveGarden(it) }
        val content = storage.readText() ?: return createDefaultGarden()
        return try {
            GardenCodec.decode(content)
        } catch (_: Exception) {
            storage.quarantineCorrupt()
            recoveryNotice = true
            createDefaultGarden().also { saveGarden(it) }
        }
    }

    fun saveGarden(garden: Garden): Boolean =
        storage.writeTextAtomic(GardenCodec.encode(garden))

    fun updateSlot(parcelId: String, row: Int, col: Int, vegetableId: String?): GardenUpdate {
        val current = loadGarden()
        val nextHistory = current.history.toMutableList()
        val updatedParcels = current.parcels.map { parcel ->
            if (parcel.id != parcelId) {
                parcel
            } else {
                val existingSlot = parcel.slots.find { it.row == row && it.col == col }
                val prevVegId = existingSlot?.vegetableId
                if (prevVegId != null && prevVegId != vegetableId) {
                    val prevFamily = VegetableCatalog.getById(prevVegId)?.family
                    if (prevFamily != null) {
                        val year = existingSlot.plantedDate?.let { epochMillisToLocalDate(it).year } ?: todayLocalDate().year
                        val entry = CropHistoryEntry(
                            parcelId = parcelId,
                            row = row,
                            col = col,
                            year = year,
                            family = prevFamily,
                            vegetableId = prevVegId,
                        )
                        if (entry !in nextHistory) nextHistory.add(entry)
                    }
                }
                val nextSlots = parcel.slots.filterNot { it.row == row && it.col == col }.toMutableList()
                if (vegetableId != null) {
                    val now = nowEpochMillis()
                    nextSlots.add(
                        existingSlot?.copy(
                            vegetableId = vegetableId,
                            plantedDate = now,
                        ) ?: ParcelSlot(row = row, col = col, vegetableId = vegetableId, plantedDate = now)
                    )
                }
                parcel.copy(slots = nextSlots)
            }
        }
        val updated = current.copy(parcels = updatedParcels, history = nextHistory)
        val saved = saveGarden(updated)
        return GardenUpdate(updated, saved)
    }

    fun addParcel(
        name: String,
        type: ParcelType,
        rows: Int,
        cols: Int,
        containerVolumeLiters: Int? = null,
    ): GardenUpdate {
        val current = loadGarden()
        val newParcel = Parcel(
            id = "parcel_${nowEpochMillis()}",
            name = name.trim().ifEmpty { "Nouvelle parcelle" },
            type = type,
            rows = rows.coerceIn(1, 8),
            cols = cols.coerceIn(1, 8),
            slots = emptyList(),
            containerVolumeLiters = containerVolumeLiters?.coerceIn(1, 2000),
        )
        val updated = current.copy(parcels = current.parcels + newParcel)
        val saved = saveGarden(updated)
        return GardenUpdate(updated, saved)
    }

    fun updateEnvironment(
        environment: GrowingEnvironment,
        orientation: Orientation? = null,
        windExposure: WindExposure? = null,
        covered: Boolean = false,
        lightSource: LightSource? = null,
        growLightHoursPerDay: Int? = null,
    ): GardenUpdate {
        val current = loadGarden()
        val updated = current.copy(
            environment = environment,
            orientation = orientation,
            windExposure = windExposure,
            covered = covered,
            lightSource = lightSource,
            growLightHoursPerDay = growLightHoursPerDay?.coerceIn(1, 24),
        )
        val saved = saveGarden(updated)
        return GardenUpdate(updated, saved)
    }

    fun deleteParcel(parcelId: String): GardenUpdate {
        val current = loadGarden()
        val remaining = current.parcels.filterNot { it.id == parcelId }
        val updated = current.copy(parcels = remaining.ifEmpty { current.parcels })
        val saved = saveGarden(updated)
        return GardenUpdate(updated, saved)
    }

    fun updateFrostDate(month: Int, day: Int): GardenUpdate {
        val current = loadGarden()
        val updated = current.copy(
            frostDate = FrostDate(month.coerceIn(1, 12), day.coerceIn(1, 31)),
        )
        val saved = saveGarden(updated)
        return GardenUpdate(updated, saved)
    }

    fun updateAltitude(meters: Int): GardenUpdate {
        val current = loadGarden()
        val updated = current.copy(
            altitudeMeters = meters.coerceIn(0, 3000),
        )
        val saved = saveGarden(updated)
        return GardenUpdate(updated, saved)
    }

    fun updateWateringConditions(mulched: Boolean, localWeather: LocalWeather): GardenUpdate {
        val current = loadGarden()
        val updated = current.copy(
            mulched = mulched,
            localWeather = localWeather,
        )
        val saved = saveGarden(updated)
        return GardenUpdate(updated, saved)
    }

    fun exportCsvString(): String {
        val garden = loadGarden()
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("Parcelle;Type;Ligne;Colonne;Plante ID;Date Plantation\n")
        garden.parcels.forEach { parcel ->
            for (r in 0 until parcel.rows) {
                for (c in 0 until parcel.cols) {
                    val slot = parcel.getSlot(r, c)
                    sb.append(
                        "${parcel.name};${parcel.type.frenchLabel};${r + 1};${c + 1};" +
                            "${slot?.vegetableId ?: "Vide"};${slot?.plantedDate ?: ""}\n"
                    )
                }
            }
        }
        return sb.toString()
    }

    private fun createDefaultGarden(): Garden {
        val sampleSlots = listOf(
            ParcelSlot(row = 0, col = 0, vegetableId = "radis"),
            ParcelSlot(row = 0, col = 1, vegetableId = "carotte"),
            ParcelSlot(row = 0, col = 2, vegetableId = "oignon"),
            ParcelSlot(row = 1, col = 0, vegetableId = "laitue"),
            ParcelSlot(row = 1, col = 1, vegetableId = "basilic"),
            ParcelSlot(row = 1, col = 2, vegetableId = "tomate"),
            ParcelSlot(row = 2, col = 0, vegetableId = "epinard"),
            ParcelSlot(row = 2, col = 1, vegetableId = "haricot_nain"),
            ParcelSlot(row = 2, col = 2, vegetableId = "tagete"),
        )

        val defaultParcel = Parcel(
            id = "parcel_demo_1",
            name = "Carré Vivant n°1 (1,20 m)",
            type = ParcelType.RAISED_BED_SQUARES,
            rows = 3,
            cols = 3,
            slots = sampleSlots,
        )

        return Garden(
            id = "garden_local",
            name = "Mon Potager",
            frostDate = FrostDate(month = 5, day = 15),
            parcels = listOf(defaultParcel),
        )
    }
}

@Composable
expect fun rememberGardenStore(): GardenStore
