package fr.pilmil.sillon.ui.doctor

import java.io.File

/** La photo analysée n'a pas à rester dans le cache de l'appli une fois l'analyse terminée. */
internal object PhotoCache {
    /** Supprime la photo ; renvoie true si le fichier n'existe plus après l'appel. Ne lève jamais d'exception. */
    fun discard(file: File): Boolean = try {
        !file.exists() || file.delete()
    } catch (_: SecurityException) {
        false
    }
}
