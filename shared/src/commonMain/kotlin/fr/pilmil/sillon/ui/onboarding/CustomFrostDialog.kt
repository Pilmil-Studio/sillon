package fr.pilmil.sillon.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.shared.resources.*
import fr.pilmil.sillon.ui.components.SillonOutlinedButton
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * Saisie de la date moyenne de dernière gelée de printemps, hors France : jour et mois par sélecteurs.
 * Une date qui n'existe pas (31 avril) est signalée et ne peut pas être validée ; le 29 février est accepté.
 */
@Composable
fun CustomFrostDialog(
    initialMonth: Int,
    initialDay: Int,
    onConfirm: (month: Int, day: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var month by rememberSaveable { mutableStateOf(initialMonth.coerceIn(1, 12)) }
    var day by rememberSaveable { mutableStateOf(initialDay.coerceIn(1, 31)) }
    val monthNames = stringArrayResource(Res.array.month_names)
    val valid = FrostDate.isValid(month, day)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.custom_frost_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.custom_frost_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Selector(
                        label = stringResource(Res.string.day_of_month),
                        options = (1..31).map { it.toString() },
                        selectedIndex = day - 1,
                        onSelect = { day = it + 1 },
                        modifier = Modifier.weight(1f),
                    )
                    Selector(
                        label = stringResource(Res.string.custom_frost_month),
                        options = monthNames,
                        selectedIndex = month - 1,
                        onSelect = { month = it + 1 },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!valid) {
                    Text(
                        stringResource(Res.string.custom_frost_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(month, day) }, enabled = valid) {
                Text(stringResource(Res.string.custom_frost_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Liste déroulante : le libellé du champ est repris dans le bouton, pour que le choix reste lisible au lecteur d'écran. */
@Composable
private fun Selector(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        SillonOutlinedButton(text = "$label : ${options.getOrElse(selectedIndex) { "" }}", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEachIndexed { index, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(index)
                        open = false
                    },
                )
            }
        }
    }
}
