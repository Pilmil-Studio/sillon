package fr.pilmil.sillon.data

import android.content.Context
import fr.pilmil.core.platform.backup.AndroidBackupVault
import fr.pilmil.sillon.model.Garden
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class GardenRepository(private val dir: File) {

    constructor(context: Context) : this(context.filesDir)

    private val gardenFile: File
        get() = File(dir, FILE_NAME)

    private val storage = AndroidGardenStorage(dir)

    /**
     * Lecture seule, sans effet de bord (ni création, ni quarantaine) : le chargement de l'appli et ses
     * garde-fous restent dans [GardenStore]. Un magasin neuf à chaque lecture évite tout état périmé.
     */
    fun readGarden(): GardenFile = GardenStore(storage).read()

    /** Le potager du disque pour un export ; échoue plutôt que d'exporter autre chose que le vrai fichier. */
    private fun requireGarden(): Garden = when (val file = readGarden()) {
        is GardenFile.Found -> file.garden
        else -> throw IOException("Potager illisible ou absent : export impossible ($file)")
    }

    /** Sauvegarde atomique : écriture dans un fichier temporaire, fsync, puis renommage. */
    @Synchronized
    fun saveGarden(garden: Garden): Boolean = GardenStore(storage).saveGarden(garden)

    /**
     * Export 1-tap vers un flux OutputStream (SAF ou fichier de partage).
     * 100% hors-ligne, sans compte, export lisible et réimportable.
     */
    fun exportToJson(outputStream: OutputStream) {
        val garden = requireGarden()
        val content = GardenCodec.encode(garden)
        outputStream.write(content.toByteArray(StandardCharsets.UTF_8))
        outputStream.flush()
    }

    /** Export chiffré (.pilmil) via AndroidBackupVault de pilmil-core (AES-256-GCM + PBKDF2). */
    fun exportToPilmil(outputStream: OutputStream, passphrase: CharArray) {
        val content = GardenCodec.encode(requireGarden())
        val container = AndroidBackupVault().encrypt(content, passphrase)
        outputStream.write(container)
        outputStream.flush()
    }

    /** Import d'une sauvegarde chiffrée (.pilmil) avec validation complète et sauvegarde `.bak`. */
    @Synchronized
    fun importFromPilmil(inputStream: InputStream, passphrase: CharArray): Result<Garden> {
        return try {
            val container = inputStream.readBytes()
            val content = AndroidBackupVault().decrypt(container, passphrase)
            val importedGarden = GardenCodec.decodeAndValidate(content)
            if (gardenFile.exists()) {
                Files.copy(gardenFile.toPath(), File(dir, "$FILE_NAME.bak").toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            if (!saveGarden(importedGarden)) throw IOException("Écriture du potager importé impossible")
            Result.success(importedGarden)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import d'une sauvegarde JSON : bornes et catalogue validés, ancien potager copié en `.bak` avant remplacement.
     * L'échec porte un [GardenValidationException] quand le fichier est refusé.
     */
    @Synchronized
    fun importFromJson(inputStream: InputStream): Result<Garden> {
        return try {
            val content = inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val buffer = CharArray(GardenCodec.MAX_FILE_CHARS + 1)
                val read = reader.readFully(buffer)
                String(buffer, 0, read)
            }
            val importedGarden = GardenCodec.decodeAndValidate(content)
            if (gardenFile.exists()) {
                Files.copy(gardenFile.toPath(), File(dir, "$FILE_NAME.bak").toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            if (!saveGarden(importedGarden)) throw IOException("Écriture du potager importé impossible")
            Result.success(importedGarden)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun java.io.Reader.readFully(buffer: CharArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    /**
     * Export CSV pour tableur (LibreOffice Calc, Excel, etc.).
     */
    fun exportToCsv(outputStream: OutputStream) {
        val garden = requireGarden()
        val writer = OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
        writer.use { out ->
            // BOM UTF-8 pour Excel
            out.write("\uFEFF")
            out.write("Parcelle;Type;Ligne;Colonne;Plante ID;Date Plantation\n")
            garden.parcels.forEach { parcel ->
                for (r in 0 until parcel.rows) {
                    for (c in 0 until parcel.cols) {
                        val slot = parcel.getSlot(r, c)
                        out.write(
                            "${parcel.name};${parcel.type.frenchLabel};${r + 1};${c + 1};" +
                            "${slot?.vegetableId ?: "Vide"};${slot?.plantedDate ?: ""}\n"
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val FILE_NAME = "sillon_garden.json"
    }
}
