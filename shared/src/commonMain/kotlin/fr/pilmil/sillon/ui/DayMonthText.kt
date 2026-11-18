package fr.pilmil.sillon.ui

import androidx.compose.runtime.Composable
import fr.pilmil.sillon.model.formatDayMonth
import fr.pilmil.sillon.shared.resources.Res
import fr.pilmil.sillon.shared.resources.day_month_pattern
import fr.pilmil.sillon.shared.resources.month_names_in_date
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** Jour + mois dans la langue de l'interface (« 15 mai », « May 15 », « 15. Mai »...). */
@Composable
fun formatDayMonth(date: LocalDate): String =
    formatDayMonth(date, stringArrayResource(Res.array.month_names_in_date), stringResource(Res.string.day_month_pattern))
