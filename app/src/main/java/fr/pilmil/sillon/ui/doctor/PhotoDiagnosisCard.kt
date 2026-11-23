package fr.pilmil.sillon.ui.doctor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.data.SillonPrefs
import fr.pilmil.sillon.engine.AbioticSymptomHint
import fr.pilmil.sillon.engine.ConfidenceTier
import fr.pilmil.sillon.engine.LeafSpotQuestion
import fr.pilmil.sillon.engine.LeafSpotTriage
import fr.pilmil.sillon.engine.PhotoDiagnosis
import fr.pilmil.sillon.engine.PhotoVerdict
import fr.pilmil.sillon.engine.SubstrateMoisture
import fr.pilmil.sillon.engine.TriageAnswer
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.shared.resources.*
import fr.pilmil.sillon.ui.components.SillonButton
import fr.pilmil.sillon.ui.components.SillonCard
import fr.pilmil.sillon.ui.components.SillonOutlinedButton
import fr.pilmil.sillon.vision.PhotoClassifier
import fr.pilmil.sillon.vision.PhotoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.io.File

/**
 * « Analyser une photo » : visible seulement si un modèle est embarqué.
 * Le résultat est une piste, jamais un verdict : il renvoie vers la fiche de la maladie,
 * ou vers la clé de diagnostic quand le modèle n'est pas assez sûr.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoDiagnosisCard(onOpenDisease: (String) -> Unit, onOpenKey: () -> Unit, plantFamily: BotanicalFamily? = null) {
    val context = LocalContext.current
    // Le modèle se charge hors du fil principal ; la carte n'apparaît que s'il est disponible.
    var classifier by remember { mutableStateOf<PhotoClassifier?>(null) }
    LaunchedEffect(Unit) { classifier = withContext(Dispatchers.IO) { PhotoClassifier.load(context) } }
    // close() attend la fin d'une éventuelle analyse en cours, puis rend toute analyse suivante « annulée ».
    DisposableEffect(classifier) { onDispose { classifier?.close() } }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var verdict by remember { mutableStateOf<PhotoVerdict?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Incrémenté à chaque analyse terminée : ramène le résultat à l'écran, même s'il est identique au précédent.
    var resultVersion by remember { mutableStateOf(0) }
    val resultRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(resultVersion) { if (resultVersion > 0) resultRequester.bringIntoView() }

    val photoFile = remember { File(context.cacheDir, "photos/feuille.jpg").apply { parentFile?.mkdirs() } }

    fun analyse(uri: Uri) {
        val active = classifier ?: return
        busy = true
        failed = false
        scope.launch {
            // Que l'analyse réussisse, échoue ou soit interrompue (écran quitté), la photo ne reste pas dans le cache.
            try {
                val result = withContext(Dispatchers.Default) { active.analyse(context, uri, plantFamily) }
                when (result) {
                    is PhotoResult.Success -> { verdict = result.verdict; failed = false; resultVersion++ }
                    is PhotoResult.Cancelled -> Unit
                    is PhotoResult.DecodeFailed, is PhotoResult.InferenceFailed -> { verdict = null; failed = true; resultVersion++ }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { PhotoCache.discard(photoFile) }
                busy = false
            }
        }
    }

    val threshold = classifier?.spec?.threshold ?: return
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.photos", photoFile) }
    var showSmartViewfinder by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) analyse(photoUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::analyse) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showSmartViewfinder = true
        } else {
            try {
                camera.launch(photoUri)
            } catch (_: ActivityNotFoundException) {
                failed = true
            }
        }
    }

    fun launchCamera() {
        val prefs = SillonPrefs(context)
        if (prefs.smartViewfinderEnabled) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                showSmartViewfinder = true
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            try {
                camera.launch(photoUri)
            } catch (_: ActivityNotFoundException) {
                failed = true
            }
        }
    }

    if (showSmartViewfinder) {
        Dialog(
            onDismissRequest = { showSmartViewfinder = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SmartCameraViewfinder(
                outputFile = photoFile,
                onPhotoCaptured = {
                    showSmartViewfinder = false
                    analyse(photoUri)
                },
                onDismiss = { showSmartViewfinder = false },
            )
        }
    }

    SillonCard(backgroundColor = MaterialTheme.colorScheme.surface, borderColor = MaterialTheme.colorScheme.outline) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.photo_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.photo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SillonButton(
                text = stringResource(Res.string.photo_take),
                icon = Icons.Filled.CameraAlt,
                enabled = !busy,
                onClick = { launchCamera() },
            )
            SillonOutlinedButton(
                text = stringResource(Res.string.photo_pick),
                icon = Icons.Filled.PhotoLibrary,
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )

            Column(
                Modifier
                    .bringIntoViewRequester(resultRequester)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                when {
                    busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(Res.string.photo_busy), style = MaterialTheme.typography.bodyMedium)
                    }
                    failed -> Text(stringResource(Res.string.photo_failed), style = MaterialTheme.typography.bodyMedium)
                    else -> verdict?.let { VerdictText(it, threshold, onOpenDisease, onOpenKey) }
                }
            }
            Text(
                stringResource(Res.string.photo_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerdictText(
    verdict: PhotoVerdict,
    threshold: Float,
    onOpenDisease: (String) -> Unit,
    onOpenKey: () -> Unit,
) {
    val tierProbable = stringResource(Res.string.photo_tier_probable)
    val tierPossible = stringResource(Res.string.photo_tier_possible)
    val tierUnsure = stringResource(Res.string.photo_tier_unsure)
    fun tier(score: Float) = when (PhotoDiagnosis.confidenceTier(score, threshold)) {
        ConfidenceTier.PROBABLE -> tierProbable
        ConfidenceTier.POSSIBLE -> tierPossible
        ConfidenceTier.UNSURE -> tierUnsure
    }
    // Le verdict, même sûr, reste une piste : la clé de diagnostic est toujours proposée.
    val keyButton: @Composable () -> Unit = {
        TextButton(onClick = onOpenKey) { Text(stringResource(Res.string.photo_open_key)) }
    }
    when (verdict) {
        is PhotoVerdict.Healthy -> {
            Text(
                stringResource(Res.string.photo_healthy, tier(verdict.confidence)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(Res.string.photo_healthy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            keyButton()
        }
        is PhotoVerdict.Disease -> {
            val name = DiseaseCatalog.getById(verdict.catalogId)?.name ?: verdict.catalogId
            Text(
                stringResource(Res.string.photo_resembles, name, tier(verdict.confidence)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = { onOpenDisease(verdict.catalogId) }) { Text(stringResource(Res.string.photo_open_sheet, name)) }
            keyButton()
        }
        is PhotoVerdict.Unsure -> {
            var selectedMoisture by remember { mutableStateOf<SubstrateMoisture?>(null) }
            var selectedHint by remember { mutableStateOf<AbioticSymptomHint?>(null) }
            val abioticLeads = remember(selectedMoisture, selectedHint) {
                PhotoDiagnosis.resolveAbioticTriage(selectedMoisture, selectedHint)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (PhotoDiagnosis.leafSpotCombinedConfidence(verdict.leads) != null) {
                    Text(
                        stringResource(Res.string.photo_leafspot),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        stringResource(Res.string.photo_no_clear),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                // Mildiou et alternariose trop proches : deux questions sur des signes visibles, puis une mise à jour des pistes.
                var answers by remember(verdict) { mutableStateOf(emptyMap<LeafSpotQuestion, TriageAnswer>()) }
                val triage = remember(verdict) { LeafSpotTriage.needsTriage(verdict.leads) }
                if (triage) {
                    LeafSpotTriageBlock(answers = answers, onAnswer = { question, answer -> answers = answers + (question to answer) })
                }
                val shownLeads = if (triage) LeafSpotTriage.updatedLeads(verdict.leads, answers) else verdict.leads
                shownLeads.forEach { (id, p) ->
                    val name = DiseaseCatalog.getById(id)?.name ?: id
                    TextButton(onClick = { onOpenDisease(id) }) { Text(stringResource(Res.string.photo_lead, tier(p), name)) }
                }
                if (triage && answers.values.any { it != TriageAnswer.UNKNOWN }) {
                    shownLeads.firstOrNull()?.let { (id, _) ->
                        Text(
                            stringResource(Res.string.photo_triage_result, DiseaseCatalog.getById(id)?.name ?: id),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    stringResource(Res.string.photo_refine),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(Res.string.photo_step_moisture),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SubstrateMoisture.entries.forEach { m ->
                        val selected = selectedMoisture == m
                        Surface(
                            modifier = Modifier.clickable {
                                selectedMoisture = if (selected) null else m
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            ),
                        ) {
                            Text(
                                text = m.frenchLabel,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                Text(
                    stringResource(Res.string.photo_step_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AbioticSymptomHint.entries.forEach { h ->
                        val selected = selectedHint == h
                        Surface(
                            modifier = Modifier.clickable {
                                selectedHint = if (selected) null else h
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            ),
                        ) {
                            Text(
                                text = h.frenchLabel,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                abioticLeads.forEach { id ->
                    val name = DiseaseCatalog.getById(id)?.name ?: id
                    TextButton(onClick = { onOpenDisease(id) }) {
                        Text(stringResource(Res.string.photo_recommended, name), fontWeight = FontWeight.SemiBold)
                    }
                }
                keyButton()
            }
        }
    }
}

/** Deux questions pour départager mildiou et alternariose, et le conseil de photographier le revers de la feuille. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeafSpotTriageBlock(
    answers: Map<LeafSpotQuestion, TriageAnswer>,
    onAnswer: (LeafSpotQuestion, TriageAnswer) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.photo_triage_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        listOf(
            LeafSpotQuestion.WHITE_FUZZ_OR_OILY to Res.string.photo_triage_white,
            LeafSpotQuestion.CONCENTRIC_RINGS to Res.string.photo_triage_rings,
        ).forEach { (question, text) ->
            Text(stringResource(text), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    TriageAnswer.YES to Res.string.answer_yes,
                    TriageAnswer.NO to Res.string.answer_no,
                    TriageAnswer.UNKNOWN to Res.string.answer_unknown,
                ).forEach { (answer, label) ->
                    val selected = answers[question] == answer
                    Surface(
                        modifier = Modifier.clickable { onAnswer(question, answer) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    ) {
                        Text(
                            text = stringResource(label),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Text(
            stringResource(Res.string.photo_triage_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
