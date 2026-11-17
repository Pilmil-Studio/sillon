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

/**
 * Résultat d'une modification. [refused] : le potager n'a pas pu être lu, rien n'a été écrit et
 * [garden] n'est alors qu'un potager vide, à ne pas afficher.
 */
data class GardenUpdate(val garden: Garden, val saved: Boolean, val refused: Boolean = false)

interface PlatformGardenStorage {
    fun exists(): Boolean
    /** Le texte du fichier, ou null si la lecture a échoué (le fichier existe mais n'est pas lisible). */
    fun readText(): String?
    fun writeTextAtomic(content: String): Boolean
    fun quarantineCorrupt()
}

/** Ce que contient le fichier du potager, sans rien modifier sur le disque. */
sealed interface GardenFile {
    data class Found(val garden: Garden) : GardenFile
    /** Première ouverture : aucun fichier. */
    data object Missing : GardenFile
    /** Le fichier se lit mais son contenu n'est pas un potager. */
    data object Corrupt : GardenFile
    /** Erreur d'entrée-sortie : le fichier existe peut-être intact, on n'y touche pas. */
    data object Unreadable : GardenFile
    /** Fichier écrit par une version plus récente de l'appli. */
    data class NewerVersion(val foundVersion: Int, val supportedVersion: Int) : GardenFile
}

/** Résultat de [GardenStore.load]. */
sealed interface GardenLoad {
    /** [recovered] : un fichier illisible a été mis de côté et un potager neuf créé. */
    data class Ready(val garden: Garden, val recovered: Boolean = false) : GardenLoad
    /** Lecture impossible pour l'instant : aucune modification possible tant qu'un nouvel essai n'a pas réussi. */
    data object ReadFailed : GardenLoad
    /** Le fichier vient d'une version plus récente de Sillon : il est laissé intact. */
    data class NewerVersion(val foundVersion: Int, val supportedVersion: Int) : GardenLoad
}

/**
 * Le potager en mémoire est la source de vérité une fois [load] réussi : les modifications
 * partent de lui et sont écrites de façon atomique. Tant que [load] n'a pas réussi,
 * toute modification est refusée, de sorte qu'un potager par défaut ne remplace jamais un fichier existant.
 */
class GardenStore(private val storage: PlatformGardenStorage) {

    private var recoveryNotice = false
    private var held: Garden? = null

    fun consumeRecoveryNotice(): Boolean {
        val pending = recoveryNotice
        recoveryNotice = false
        return pending
    }

    /** Lit le fichier sans rien écrire ni déplacer. Sert aussi aux exports et aux rappels. */
    fun read(): GardenFile {
        if (!storage.exists()) return GardenFile.Missing
        val content = storage.readText() ?: return GardenFile.Unreadable
        return try {
            GardenFile.Found(GardenCodec.decode(content))
        } catch (e: GardenVersionException) {
            GardenFile.NewerVersion(e.foundVersion, e.supportedVersion)
        } catch (_: Exception) {
            GardenFile.Corrupt
        }
    }

    /**
     * Charge le potager. Fichier absent : potager par défaut, enregistré. Fichier corrompu :
     * mis en quarantaine, potager neuf et [consumeRecoveryNotice]. Erreur de lecture ou version
     * trop récente : rien n'est écrit, rien n'est gardé en mémoire et les modifications sont refusées.
     */
    fun load(): GardenLoad {
        val loaded = when (val file = read()) {
            is GardenFile.Found -> GardenLoad.Ready(file.garden)
            GardenFile.Missing -> {
                val garden = createDefaultGarden()
                if (!saveGarden(garden)) return GardenLoad.ReadFailed
                GardenLoad.Ready(garden)
            }
            GardenFile.Corrupt -> {
                storage.quarantineCorrupt()
                recoveryNotice = true
                val garden = createDefaultGarden()
                saveGarden(garden)
                GardenLoad.Ready(garden, recovered = true)
            }
            GardenFile.Unreadable -> GardenLoad.ReadFailed
            is GardenFile.NewerVersion -> GardenLoad.NewerVersion(file.foundVersion, file.supportedVersion)
        }
        if (loaded is GardenLoad.Ready) held = loaded.garden
        return loaded
    }

    /** Remplace le potager en mémoire par un potager déjà écrit sur disque (import). */
    fun adopt(garden: Garden) {
        held = garden
    }

    fun saveGarden(garden: Garden): Boolean =
        storage.writeTextAtomic(GardenCodec.encode(garden))

    /** Applique [change] au potager en mémoire puis l'écrit ; refuse si aucune lecture n'a réussi. */
    private fun mutate(change: (Garden) -> Garden): GardenUpdate {
        val base = held ?: (load() as? GardenLoad.Ready)?.garden
            ?: return GardenUpdate(createDefaultGarden(), saved = false, refused = true)
        val updated = change(base)
        held = updated
        return GardenUpdate(updated, saveGarden(updated))
    }

    fun updateSlot(parcelId: String, row: Int, col: Int, vegetableId: String?): GardenUpdate = mutate { current ->
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
        current.copy(parcels = updatedParcels, history = nextHistory)
    }

    fun addParcel(
        name: String,
        type: ParcelType,
        rows: Int,
        cols: Int,
        containerVolumeLiters: Int? = null,
    ): GardenUpdate = mutate { current ->
        val newParcel = Parcel(
            id = "parcel_${nowEpochMillis()}",
            name = name.trim().ifEmpty { "Nouvelle parcelle" },
            type = type,
            rows = rows.coerceIn(1, 8),
            cols = cols.coerceIn(1, 8),
            slots = emptyList(),
            containerVolumeLiters = containerVolumeLiters?.coerceIn(1, 2000),
        )
        current.copy(parcels = current.parcels + newParcel)
    }

    fun updateEnvironment(
        environment: GrowingEnvironment,
        orientation: Orientation? = null,
        windExposure: WindExposure? = null,
        covered: Boolean = false,
        lightSource: LightSource? = null,
        growLightHoursPerDay: Int? = null,
    ): GardenUpdate = mutate { current ->
        current.copy(
            environment = environment,
            orientation = orientation,
            windExposure = windExposure,
            covered = covered,
            lightSource = lightSource,
            growLightHoursPerDay = growLightHoursPerDay?.coerceIn(1, 24),
        )
    }

    fun deleteParcel(parcelId: String): GardenUpdate = mutate { current ->
        val remaining = current.parcels.filterNot { it.id == parcelId }
        current.copy(parcels = remaining.ifEmpty { current.parcels })
    }

    fun updateFrostDate(month: Int, day: Int): GardenUpdate = mutate { current ->
        current.copy(
            frostDate = FrostDate(month.coerceIn(1, 12), day.coerceIn(1, 31)),
        )
    }

    /**
     * Enregistre les dates saisies hors France et les active. La date du département ([Garden.frostDate]) n'est pas touchée.
     * Une date qui n'existe pas au calendrier (31 avril) est refusée sans rien modifier.
     */
    fun updateCustomFrost(month: Int, day: Int): GardenUpdate = mutate { current ->
        if (!FrostDate.isValid(month, day)) current
        else current.copy(customFrostDate = FrostDate(month, day), useCustomFrost = true)
    }

    /** Revient au département (false) ou réactive les dates saisies (true, seulement s'il y en a). Rien n'est effacé. */
    fun setUseCustomFrost(use: Boolean): GardenUpdate = mutate { current ->
        current.copy(useCustomFrost = use && current.customFrostDate != null)
    }

    fun updateAltitude(meters: Int): GardenUpdate = mutate { current ->
        current.copy(
            altitudeMeters = meters.coerceIn(0, 3000),
        )
    }

    fun updateWateringConditions(mulched: Boolean, localWeather: LocalWeather): GardenUpdate = mutate { current ->
        current.copy(
            mulched = mulched,
            localWeather = localWeather,
        )
    }

    /** Le tableur du potager en mémoire, ou null tant qu'aucune lecture n'a réussi. */
    fun exportCsvString(): String? {
        val garden = held ?: (load() as? GardenLoad.Ready)?.garden ?: return null
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
