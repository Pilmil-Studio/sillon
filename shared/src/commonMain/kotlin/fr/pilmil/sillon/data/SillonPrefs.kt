package fr.pilmil.sillon.data

import androidx.compose.runtime.Composable
import fr.pilmil.sillon.model.Climate

interface SillonPrefs {
    var onboarded: Boolean
    var departmentCode: String?
    var sowingReminders: Boolean
    var smartViewfinderEnabled: Boolean
    var dashboardVisits: Int

    val department: DepartmentFrost?
        get() = departmentCode?.let(FrenchFrostCatalog::getByCode)

    /** Climat du jardin, déduit du département ; tempéré tant qu'aucun département n'est choisi. */
    val climate: Climate
        get() = department?.climate ?: Climate.TEMPERATE
}

@Composable
expect fun rememberSillonPrefs(): SillonPrefs
