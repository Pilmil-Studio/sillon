package fr.pilmil.sillon.ui.doctor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.pilmil.sillon.engine.DiagnosticContext
import fr.pilmil.sillon.engine.DiagnosticJournal
import fr.pilmil.sillon.model.Disease
import fr.pilmil.sillon.model.JournalDraft
import fr.pilmil.sillon.model.nowEpochMillis
import fr.pilmil.sillon.shared.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * « Noter dans le journal de ce carré » en deux temps : on note la cause, la date, la plante et une action
 * facultative ; puis, une fois l'entrée enregistrée, on propose « Revérifier dans N jours ? » avec une durée
 * par défaut adaptée au problème. La photo d'un diagnostic n'est jamais enregistrée : seul du texte est gardé.
 * Aucun des deux temps n'est obligatoire : on peut annuler, ou ne pas programmer de rappel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalNoteDialog(
    context: DiagnosticContext,
    disease: Disease?,
    onNote: (JournalDraft) -> Unit,
    onFollowUp: (entryId: String, days: Int?) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    var savedId by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(DiagnosticJournal.defaultFollowUpDays(disease)) }
    val noted = savedId

    if (noted == null) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = {
                Text(
                    text = stringResource(Res.string.journal_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            Res.string.journal_sheet_cause,
                            disease?.name ?: stringResource(Res.string.journal_cause_uncertain),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(Res.string.journal_sheet_plant, context.vegetable.name),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(Res.string.journal_sheet_today),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = action,
                        onValueChange = { action = it.take(DiagnosticJournal.MAX_ACTION_CHARS) },
                        label = { Text(stringResource(Res.string.journal_action_label)) },
                        placeholder = { Text(stringResource(Res.string.journal_action_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = "diag_${nowEpochMillis()}"
                        onNote(JournalDraft(id, context.parcelId, context.row, context.col, disease?.id, action))
                        savedId = id
                    },
                ) { Text(stringResource(Res.string.journal_save)) }
            },
            dismissButton = {
                TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    } else {
        AlertDialog(
            onDismissRequest = onDone,
            title = {
                Text(
                    text = stringResource(Res.string.journal_saved_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = pluralStringResource(Res.plurals.followup_question, days, days),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DiagnosticJournal.FOLLOW_UP_CHOICES.forEach { choice ->
                            val selected = choice == days
                            Surface(
                                modifier = Modifier.clickable { days = choice },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                ),
                            ) {
                                Text(
                                    text = pluralStringResource(Res.plurals.followup_days_chip, choice, choice),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(Res.string.followup_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onFollowUp(noted, days)
                        onDone()
                    },
                ) { Text(stringResource(Res.string.followup_set)) }
            },
            dismissButton = {
                TextButton(onClick = onDone) { Text(stringResource(Res.string.followup_none)) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}
