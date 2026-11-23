package fr.pilmil.sillon.ui.doctor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pilmil.sillon.engine.DiagnosticContext
import fr.pilmil.sillon.engine.DiagnosticEngine
import fr.pilmil.sillon.engine.Season
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.model.Disease
import fr.pilmil.sillon.model.JournalDraft
import fr.pilmil.sillon.model.PlantOrgan
import fr.pilmil.sillon.shared.resources.*
import fr.pilmil.sillon.ui.components.SillonBadge
import fr.pilmil.sillon.ui.components.SillonButton
import fr.pilmil.sillon.ui.components.SillonCard
import fr.pilmil.sillon.ui.components.SillonOutlinedButton
import fr.pilmil.sillon.ui.theme.SillonTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Écran docteur. Sans [context], c'est l'outil autonome : toutes les causes de l'organe choisi, comme avant.
 * Avec un [context] (on arrive d'une plantation), les causes sont rangées d'après la plante : les plus probables
 * d'abord, les autres restent à un toucher. Choisir une cause propose de la noter dans le journal du carré.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoctorScreen(
    diseases: List<Disease>,
    onSelectDisease: (Disease) -> Unit = {},
    photoDiagnosisContent: (@Composable (onOpenDisease: (String) -> Unit, onOpenKey: () -> Unit, plantFamily: BotanicalFamily?) -> Unit)? = null,
    context: DiagnosticContext? = null,
    onBackToGarden: () -> Unit = {},
    onNoteProblem: (JournalDraft) -> Unit = {},
    onFollowUp: (entryId: String, days: Int?) -> Unit = { _, _ -> },
) {
    var selectedOrgan by remember { mutableStateOf<PlantOrgan?>(null) }
    var selectedDiseaseId by remember { mutableStateOf<String?>(null) }
    var showOthers by remember { mutableStateOf(false) }
    // Cause en cours de notation (null : aucune fenêtre ouverte).
    var noting by remember { mutableStateOf<NoteTarget?>(null) }

    val ranked = remember(diseases, selectedOrgan, context) {
        DiagnosticEngine(diseases).rankCauses(context, selectedOrgan, diseases)
    }
    val others = if (showOthers) ranked.others else emptyList()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val contextItems = if (context != null) 1 else 0
    val photoItems = if (photoDiagnosisContent != null) 1 else 0
    // En-tête, carte photo, sélecteur d'organe et titre : éléments qui précèdent la liste des causes.
    val keyStartIndex = contextItems + 1 + photoItems
    val listOffset = keyStartIndex + 2

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Contexte de la plante quand on arrive du potager
        if (context != null) item {
            ContextCard(context = context, onBack = onBackToGarden)
        }

        // En-tête
        item {
            Column {
                Text(
                    text = stringResource(Res.string.doctor_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.doctor_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Diagnostic photo sur le téléphone, seulement si un modèle est embarqué.
        if (photoDiagnosisContent != null) item {
            photoDiagnosisContent(
                { id ->
                    // Ouvre la fiche dans la liste complète, puis y fait défiler l'écran.
                    selectedOrgan = null
                    selectedDiseaseId = id
                    val full = DiagnosticEngine(diseases).rankCauses(context, null, diseases)
                    val inLikely = full.likely.indexOfFirst { it.id == id }
                    val inOthers = full.others.indexOfFirst { it.id == id }
                    val index = when {
                        inLikely >= 0 -> listOffset + inLikely
                        inOthers >= 0 -> {
                            showOthers = true
                            // Les autres causes viennent après les probables et leur titre.
                            listOffset + full.likely.size + 1 + inOthers
                        }
                        else -> -1
                    }
                    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                },
                // Amène le sélecteur d'organe, premier pas de la clé de diagnostic.
                { scope.launch { listState.animateScrollToItem(keyStartIndex) } },
                context?.family,
            )
        }

        // Sélecteur d'organe touché
        item {
            Column {
                Text(
                    text = stringResource(Res.string.doctor_step_organ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                val organs = listOf(
                    null to stringResource(Res.string.organ_all),
                    PlantOrgan.LEAF to stringResource(Res.string.organ_leaf),
                    PlantOrgan.STEM to stringResource(Res.string.organ_stem),
                    PlantOrgan.SEEDLING to stringResource(Res.string.organ_seedling),
                    PlantOrgan.FRUIT to stringResource(Res.string.organ_fruit),
                    PlantOrgan.ROOT to stringResource(Res.string.organ_root),
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(organs) { (organ, label) ->
                        val isSelected = selectedOrgan == organ
                        Surface(
                            modifier = Modifier.clickable { selectedOrgan = organ },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            ),
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }

        // Titre liste affections
        item {
            Text(
                text = if (context != null) {
                    stringResource(Res.string.doctor_likely_title, ranked.likely.size)
                } else {
                    stringResource(Res.string.doctor_step_diagnoses, ranked.likely.size)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }

        // Liste des maladies identifiées
        items(ranked.likely, key = { it.id }) { disease ->
            val isExpanded = selectedDiseaseId == disease.id
            DiseaseCard(
                disease = disease,
                isExpanded = isExpanded,
                onToggleExpand = {
                    selectedDiseaseId = if (isExpanded) null else disease.id
                },
                onNote = if (context != null) ({ noting = NoteTarget(disease) }) else null,
            )
        }

        // Autres causes : moins fréquentes sur cette plante, mais jamais cachées pour de bon.
        if (context != null && ranked.others.isNotEmpty()) item(key = "others-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.doctor_others_title, ranked.others.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showOthers = !showOthers }) {
                    Text(stringResource(if (showOthers) Res.string.doctor_others_hide else Res.string.doctor_others_show))
                }
            }
        }
        items(others, key = { "other-" + it.id }) { disease ->
            val isExpanded = selectedDiseaseId == disease.id
            DiseaseCard(
                disease = disease,
                isExpanded = isExpanded,
                onToggleExpand = {
                    selectedDiseaseId = if (isExpanded) null else disease.id
                },
                onNote = if (context != null) ({ noting = NoteTarget(disease) }) else null,
            )
        }

        if (context != null) item(key = "uncertain") {
            SillonOutlinedButton(
                text = stringResource(Res.string.doctor_note_uncertain),
                icon = Icons.Filled.Search,
                onClick = { noting = NoteTarget(null) },
            )
        }
    }

    val target = noting
    if (context != null && target != null) {
        JournalNoteDialog(
            context = context,
            disease = target.disease,
            onNote = onNoteProblem,
            onFollowUp = onFollowUp,
            onDone = {
                noting = null
                onBackToGarden()
            },
            onCancel = { noting = null },
        )
    }
}

/** Ce que la personne note : une cause du catalogue, ou null pour « cause incertaine ». */
private class NoteTarget(val disease: Disease?)

/** Carte de contexte : la plante, l'emplacement, la mise en place et la saison, avec le retour au potager. */
@Composable
private fun ContextCard(context: DiagnosticContext, onBack: () -> Unit) {
    SillonCard(backgroundColor = MaterialTheme.colorScheme.primaryContainer, borderColor = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.doctor_context_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(Res.string.doctor_context_plant, context.vegetable.name, context.family.frenchLabel),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = context.parcelName + " · " + stringResource(Res.string.garden_slot_title, context.row + 1, context.col + 1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            val days = context.daysSincePlanting
            val planted = when {
                days == null -> null
                days == 0 -> stringResource(Res.string.doctor_context_planted_today)
                else -> pluralStringResource(Res.plurals.doctor_context_planted, days, days)
            }
            val season = context.season?.let { stringResource(seasonRes(it)) }
            val line = listOfNotNull(planted, season).joinToString(" · ")
            if (line.isNotEmpty()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            SillonOutlinedButton(
                text = stringResource(Res.string.doctor_context_back),
                icon = Icons.Filled.Yard,
                onClick = onBack,
            )
        }
    }
}

private fun seasonRes(season: Season) = when (season) {
    Season.SPRING -> Res.string.doctor_context_season_spring
    Season.SUMMER -> Res.string.doctor_context_season_summer
    Season.AUTUMN -> Res.string.doctor_context_season_autumn
    Season.WINTER -> Res.string.doctor_context_season_winter
}

@Composable
private fun DiseaseCard(
    disease: Disease,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onNote: (() -> Unit)? = null,
) {
    SillonCard(
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        onClick = onToggleExpand,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when (disease.pathogenType.name) {
                                "FUNGUS" -> SillonTheme.colors.alertContainer
                                "PEST" -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (disease.pathogenType.name == "PEST") Icons.Filled.PestControl else Icons.Filled.Healing,
                        contentDescription = null,
                        tint = when (disease.pathogenType.name) {
                            "FUNGUS" -> SillonTheme.colors.onAlertContainer
                            "PEST" -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = disease.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    disease.latinName?.let { latinName ->
                        Text(
                            text = latinName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
                SillonBadge(
                    text = disease.pathogenType.frenchLabel.take(15) + "...",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Organes touchés
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                disease.affectedOrgans.forEach { organ ->
                    SillonBadge(
                        text = organ.frenchLabel,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Détails et remèdes bio si étendu
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Symptômes
                    Text(
                        text = stringResource(Res.string.doctor_symptoms),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    disease.symptoms.forEach { symptom ->
                        Text(
                            text = "• $symptom",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Remèdes biologiques
                    Text(
                        text = stringResource(Res.string.doctor_remedies),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    disease.organicRemedies.forEach { remedy ->
                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = remedy,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp,
                            )
                        }
                    }

                    // Noter la cause dans le journal du carré, quand on vient d'une plantation
                    if (onNote != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SillonButton(
                            text = stringResource(Res.string.doctor_note_action),
                            icon = Icons.Filled.Check,
                            onClick = onNote,
                        )
                    }

                    // Prévention
                    if (disease.preventiveMeasures.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(Res.string.doctor_prevention),
                            style = MaterialTheme.typography.labelSmall,
                            color = SillonTheme.colors.synergy,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        disease.preventiveMeasures.forEach { prev ->
                            Text(
                                text = "→ $prev",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
