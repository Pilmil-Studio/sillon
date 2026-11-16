package fr.pilmil.sillon.data

import fr.pilmil.sillon.model.Garden
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Une sauvegarde refusée, avec un message destiné à la personne (pas à un journal). */
class GardenValidationException(message: String) : Exception(message)

/** Le fichier vient d'une version de Sillon plus récente que celle-ci : on ne le lit pas, on ne l'écrase pas. */
class GardenVersionException(val foundVersion: Int, val supportedVersion: Int) :
    Exception("Version de potager $foundVersion non prise en charge (maximum $supportedVersion).")

/**
 * Sérialisation et validation du potager, sans dépendance Android : testable en JVM pure.
 */
object GardenCodec {
    /** Version du format du fichier. Les fichiers sans champ `schemaVersion` sont en version 1. */
    const val SCHEMA_VERSION = 1
    const val MAX_PARCELS = 100
    const val MAX_SIDE = 50
    const val MAX_FILE_CHARS = 5_000_000
    private const val MIN_DATE = 946_684_800_000L // 1er janvier 2000
    private const val DAY_MS = 86_400_000L

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(garden: Garden): String {
        val fields = json.encodeToJsonElement(Garden.serializer(), garden).jsonObject
        val versioned = JsonObject(mapOf("schemaVersion" to JsonPrimitive(SCHEMA_VERSION)) + fields)
        return json.encodeToString(JsonElement.serializer(), versioned)
    }

    /**
     * Lecture brute, sans contrôle des bornes : sert au fichier local écrit par l'appli elle-même.
     * Lève [GardenVersionException] pour une version plus récente que [SCHEMA_VERSION].
     */
    fun decode(content: String): Garden {
        val root = json.parseToJsonElement(content).jsonObject
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
        if (version > SCHEMA_VERSION) throw GardenVersionException(version, SCHEMA_VERSION)
        return json.decodeFromJsonElement(Garden.serializer(), migrate(root, version))
    }

    /**
     * Point d'accroche des migrations : amène un document de la version [from] à [SCHEMA_VERSION].
     * Aujourd'hui, la version 1 est la seule : le format ne change pas. Une version 2 ajoutera ici
     * un `if (from < 2) { ... }` qui réécrit l'objet avant le décodage.
     *
     * Les identifiants de plantes disparus du catalogue ([VegetableCatalog.LEGACY_IDS]) sont remplacés
     * à chaque lecture, quelle que soit la version : un potager, une sauvegarde JSON ou un fichier
     * `.pilmil` enregistrés avant la fusion de deux fiches continuent de s'ouvrir. L'opération ne change
     * rien à un fichier déjà à jour, donc le numéro de version reste 1 et les anciennes versions de
     * l'appli lisent toujours les fichiers écrits par celle-ci.
     */
    private fun migrate(root: JsonObject, from: Int): JsonObject =
        renameLegacyVegetableIds(root) as JsonObject

    /** Remplace toute valeur de champ `vegetableId` (cases et historique de cultures) par l'identifiant courant. */
    internal fun renameLegacyVegetableIds(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.mapValues { (key, value) ->
                if (key == "vegetableId" && value is JsonPrimitive && value.isString) {
                    JsonPrimitive(VegetableCatalog.canonicalId(value.content))
                } else {
                    renameLegacyVegetableIds(value)
                }
            },
        )
        is JsonArray -> JsonArray(element.map { renameLegacyVegetableIds(it) })
        else -> element
    }

    /** Lecture d'un fichier importé : refuse tout ce qui sortirait du cadre de l'appli. */
    fun decodeAndValidate(content: String, now: Long = Clock.System.now().toEpochMilliseconds()): Garden {
        if (content.length > MAX_FILE_CHARS) throw GardenValidationException("Ce fichier est trop volumineux pour être une sauvegarde Sillon.")
        val garden = try {
            decode(content)
        } catch (e: GardenVersionException) {
            throw GardenValidationException("Cette sauvegarde a été créée par une version plus récente de Sillon. Mettez l'appli à jour pour la lire.")
        } catch (e: Exception) {
            throw GardenValidationException("Ce fichier n'est pas une sauvegarde Sillon lisible. Choisissez le fichier .json exporté depuis Sillon.")
        }
        validate(garden, now)
        return garden
    }

    fun validate(garden: Garden, now: Long = Clock.System.now().toEpochMilliseconds()) {
        fun refuse(reason: String): Nothing = throw GardenValidationException("Sauvegarde refusée : $reason")
        if (garden.parcels.size > MAX_PARCELS) refuse("plus de $MAX_PARCELS parcelles.")
        if (garden.parcels.map { it.id }.toSet().size != garden.parcels.size) refuse("deux parcelles portent le même identifiant.")
        garden.growLightHoursPerDay?.let { hours ->
            if (hours !in 1..24) refuse("durée d'éclairage invalide ($hours h/j, doit être entre 1 et 24).")
        }
        garden.parcels.forEach { parcel ->
            if (parcel.rows !in 1..MAX_SIDE || parcel.cols !in 1..MAX_SIDE) {
                refuse("la parcelle « ${parcel.name} » doit avoir entre 1 et $MAX_SIDE lignes et colonnes (reçu : ${parcel.rows} × ${parcel.cols}).")
            }
            parcel.containerVolumeLiters?.let { vol ->
                if (vol !in 1..2000) refuse("volume de pot invalide dans « ${parcel.name} » ($vol L).")
            }
            val seen = HashSet<Pair<Int, Int>>()
            parcel.slots.forEach { slot ->
                if (slot.row !in 0 until parcel.rows || slot.col !in 0 until parcel.cols) {
                    refuse("une case de « ${parcel.name} » est hors de la parcelle (ligne ${slot.row + 1}, colonne ${slot.col + 1}).")
                }
                if (!seen.add(slot.row to slot.col)) refuse("« ${parcel.name} » contient deux plantes dans la même case.")
                slot.vegetableId?.let { id ->
                    if (VegetableCatalog.getById(id) == null) refuse("plante inconnue « $id ».")
                }
                slot.plantedDate?.let { date ->
                    if (date < MIN_DATE || date > now + DAY_MS) refuse("date de plantation invalide dans « ${parcel.name} ».")
                }
            }
        }
    }
}
