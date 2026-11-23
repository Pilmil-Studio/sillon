package fr.pilmil.sillon.ui.garden

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import fr.pilmil.sillon.ui.components.SillonOutlinedButton
import fr.pilmil.sillon.ui.evidenceLabelRes
import fr.pilmil.sillon.ui.rememberReduceMotion
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pilmil.sillon.engine.Growth
import fr.pilmil.sillon.engine.GrowthEngine
import fr.pilmil.sillon.engine.GrowthStage
import fr.pilmil.sillon.engine.RotationEngine
import fr.pilmil.sillon.engine.RotationSeverity
import fr.pilmil.sillon.engine.DiagnosticJournal
import fr.pilmil.sillon.model.Climate
import fr.pilmil.sillon.model.DiagnosticJournalEntry
import fr.pilmil.sillon.model.FollowUpAnswer
import fr.pilmil.sillon.model.JournalStatus
import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.data.VegetableCatalog
import fr.pilmil.sillon.ui.formatDayMonth
import fr.pilmil.sillon.model.Garden
import fr.pilmil.sillon.model.GrowingEnvironment
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelSlot
import fr.pilmil.sillon.model.ParcelType
import fr.pilmil.sillon.model.Vegetable
import fr.pilmil.sillon.model.badRelationsWith
import fr.pilmil.sillon.model.goodRelationsWith
import fr.pilmil.sillon.shared.resources.*
import fr.pilmil.sillon.ui.components.SillonBadge
import fr.pilmil.sillon.ui.components.SillonButton
import fr.pilmil.sillon.ui.components.SillonCard
import fr.pilmil.sillon.ui.components.SillonStepper
import fr.pilmil.sillon.ui.components.VegetableIcon
import fr.pilmil.sillon.ui.theme.SillonPalette
import fr.pilmil.sillon.ui.theme.SillonTheme
import fr.pilmil.sillon.model.daysBetween
import fr.pilmil.sillon.model.epochMillisToLocalDate
import fr.pilmil.sillon.model.todayLocalDate
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GardenScreen(
    garden: Garden,
    vegetables: List<Vegetable>,
    onUpdateSlot: (parcelId: String, row: Int, col: Int, vegetableId: String?) -> Unit,
    onAddParcel: (name: String, type: ParcelType, rows: Int, cols: Int) -> Unit,
    onDeleteParcel: (parcelId: String) -> Unit,
    onAddParcelWithVolume: (name: String, type: ParcelType, rows: Int, cols: Int, containerVolumeLiters: Int?) -> Unit = { n, t, r, c, _ ->
        onAddParcel(n, t, r, c)
    },
    today: LocalDate = remember { todayLocalDate() },
    initialSelectedCell: Pair<Int, Int>? = null,
    initialPerspective: Boolean = true,
    departmentCode: String? = null,
    skyClock: SkyClock? = null,
    onReportProblem: (parcelId: String, row: Int, col: Int) -> Unit = { _, _, _ -> },
    onAnswerFollowUp: (entryId: String, answer: FollowUpAnswer) -> Unit = { _, _ -> },
    focus: GardenFocus? = null,
    onFocusConsumed: () -> Unit = {},
) {
    var selectedParcelIndex by remember { mutableStateOf(0) }
    var selectedSlotCoord by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var highlightedCell by remember { mutableStateOf(initialSelectedCell) }
    var perspectiveView by remember { mutableStateOf(initialPerspective) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val reduceMotion = rememberReduceMotion()
    val skyPlace = remember(departmentCode) { SkyPlace.forDepartment(departmentCode) }
    var showAddParcelDialog by remember { mutableStateOf(false) }

    val activeParcel = garden.parcels.getOrNull(selectedParcelIndex) ?: garden.parcels.firstOrNull()

    // Arrivée depuis un rappel ou depuis l'accueil : on ouvre la case concernée.
    LaunchedEffect(focus) {
        val target = focus ?: return@LaunchedEffect
        val index = garden.parcels.indexOfFirst { it.id == target.parcelId }
        if (index >= 0) {
            selectedParcelIndex = index
            highlightedCell = target.row to target.col
            selectedSlotCoord = target.row to target.col
        }
        onFocusConsumed()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // En-tête compact et choix de la vue
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.garden_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ViewChip(stringResource(Res.string.garden_view_perspective), perspectiveView) { perspectiveView = true }
                    ViewChip(stringResource(Res.string.garden_view_flat), !perspectiveView) { perspectiveView = false }
                }
            }
        }

        // Onglets des parcelles existantes
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(garden.parcels.indices.toList()) { index ->
                        val parcel = garden.parcels[index]
                        val isSelected = index == selectedParcelIndex
                        Surface(
                            modifier = Modifier.clickable {
                                selectedParcelIndex = index
                                highlightedCell = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            ),
                        ) {
                            Text(
                                text = parcel.name,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showAddParcelDialog = true },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(Res.string.garden_add_parcel),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        // Grille interactive de la parcelle active
        if (activeParcel != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SillonBadge(
                        text = activeParcel.type.frenchLabel,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    activeParcel.containerVolumeLiters?.let { vol ->
                        SillonBadge(
                            text = stringResource(Res.string.garden_volume, vol),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            textColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (activeParcel.type.isContainerBased) {
                        Text(
                            text = stringResource(Res.string.garden_renew_substrate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                // Changement de vue : glissement court et fondu, dans le sens du déplacement (vue à plat à droite de la
                // perspective, carré suivant à droite du précédent). Sans animation si le système le demande.
                AnimatedContent(
                    targetState = GardenViewKey(perspectiveView, if (perspectiveView) -1 else selectedParcelIndex),
                    transitionSpec = { gardenViewTransition(reduceMotion) },
                    label = "vue du potager",
                ) { view ->
                    if (view.perspective) {
                        GardenScene(
                            parcels = garden.parcels,
                            selectedIndex = garden.parcels.indexOf(activeParcel),
                            vegetables = vegetables,
                            today = today,
                            selectedCell = highlightedCell,
                            onSelectParcel = {
                                selectedParcelIndex = it
                                highlightedCell = null
                            },
                            onCellClick = { r, c ->
                                highlightedCell = Pair(r, c)
                                selectedSlotCoord = Pair(r, c)
                            },
                            isDark = isDark,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                            skyPlace = skyPlace,
                            skyClock = skyClock,
                            journal = garden.diagnosticJournal,
                        )
                    } else {
                        ParcelGridView(
                            parcel = garden.parcels.getOrNull(view.parcelIndex) ?: activeParcel,
                            vegetables = vegetables,
                            journal = garden.diagnosticJournal,
                            onSlotClick = { r, c ->
                                highlightedCell = Pair(r, c)
                                selectedSlotCoord = Pair(r, c)
                            },
                        )
                    }
                }
            }

            // Légende des alertes & synergies
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(Res.string.garden_legend_positive),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(SillonTheme.colors.alert),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(Res.string.garden_legend_alert),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { NeighborhoodCard(activeParcel, vegetables) }
        } else {
            item {
                Text(
                    text = stringResource(Res.string.garden_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Modal Bottom Sheet pour choisir ou modifier la plante de la case
    if (selectedSlotCoord != null && activeParcel != null) {
        val (row, col) = selectedSlotCoord!!
        val currentSlot = activeParcel.getSlot(row, col)
        val currentVeg = vegetables.find { it.id == currentSlot?.vegetableId }
        val currentGrowth = slotGrowth(currentSlot, currentVeg, remember { todayLocalDate() })

        ModalBottomSheet(
            onDismissRequest = { selectedSlotCoord = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = stringResource(Res.string.garden_slot_title, row + 1, col + 1),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (currentVeg != null) {
                        stringResource(Res.string.garden_slot_current, currentVeg.name)
                    } else {
                        stringResource(Res.string.garden_slot_empty)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (currentVeg != null && currentGrowth != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StageVegetableVisual(currentVeg, currentGrowth, iconSize = 32.dp, emojiSize = 28.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentGrowth.summary(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            GrowthBar(growth = currentGrowth, height = 8)
                        }
                    }
                }

                if (currentVeg != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SillonOutlinedButton(
                        text = stringResource(Res.string.garden_slot_problem_button),
                        icon = Icons.Filled.Healing,
                        onClick = {
                            onReportProblem(activeParcel.id, row, col)
                            selectedSlotCoord = null
                        },
                    )
                }

                SlotJournal(
                    garden = garden,
                    parcelId = activeParcel.id,
                    row = row,
                    col = col,
                    today = today,
                    onAnswer = onAnswerFollowUp,
                )

                if (currentVeg != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SillonOutlinedButton(
                        text = stringResource(Res.string.garden_slot_clear),
                        icon = Icons.Filled.Clear,
                        onClick = {
                            onUpdateSlot(activeParcel.id, row, col, null)
                            selectedSlotCoord = null
                        },
                        borderColor = SillonTheme.colors.alert,
                        textColor = SillonTheme.colors.alert,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                val slotHistory = remember(garden.history, activeParcel.id, row, col) {
                    garden.history.filter { it.parcelId == activeParcel.id && it.row == row && it.col == col }
                        .sortedByDescending { it.year }
                }
                if (slotHistory.isNotEmpty()) {
                    val historyText = slotHistory.take(3).joinToString(", ") {
                        val label = vegetables.find { v -> v.id == it.vegetableId }?.name ?: it.vegetableId
                        "$label (${it.year})"
                    }
                    Text(
                        text = stringResource(Res.string.garden_slot_history, historyText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                val rotationEngine = remember { RotationEngine() }
                Text(
                    text = stringResource(Res.string.garden_slot_plant),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(vegetables) { veg ->
                        val rotationIssues = remember(veg.id, slotHistory, today.year, garden.diagnosticJournal) {
                            rotationEngine.checkRotation(veg, activeParcel.id, row, col, today.year, slotHistory, garden.diagnosticJournal)
                                .sortedBy { if (it.severity == RotationSeverity.SOIL_DISEASE) 0 else 1 }
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateSlot(activeParcel.id, row, col, veg.id)
                                    selectedSlotCoord = null
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                VegetableIcon(
                                    vegetable = veg,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = veg.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Text(
                                        text = "${veg.family.frenchLabel} • ${stringResource(Res.string.catalog_density, veg.squareFootDensity)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (veg.needsMoreSpace) {
                                        Text(
                                            text = stringResource(Res.string.garden_needs_space),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SillonPalette.AlertAmber,
                                        )
                                    }
                                    if (veg.indicative) {
                                        Text(
                                            text = stringResource(Res.string.garden_indicative),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontStyle = FontStyle.Italic,
                                        )
                                    }
                                    rotationIssues.firstOrNull()?.let { issue ->
                                        val issueColor = when (issue.severity) {
                                            RotationSeverity.CONFLICT_ERROR -> SillonPalette.AlertAmber
                                            RotationSeverity.EXHAUSTION_WARNING -> MaterialTheme.colorScheme.secondary
                                            RotationSeverity.BENEFICIAL -> MaterialTheme.colorScheme.primary
                                            RotationSeverity.SOIL_DISEASE -> SillonPalette.AlertAmber
                                        }
                                        Text(
                                            text = if (issue.severity == RotationSeverity.SOIL_DISEASE) {
                                                stringResource(Res.string.rotation_soil_label)
                                            } else {
                                                issue.severity.frenchLabel
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = issueColor,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                                if (currentSlot?.vegetableId == veg.id) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogue d'ajout de parcelle
    if (showAddParcelDialog) {
        val defaultType = when (garden.environment) {
            GrowingEnvironment.OUTDOOR -> ParcelType.RAISED_BED_SQUARES
            GrowingEnvironment.BALCONY -> ParcelType.CONTAINER
            GrowingEnvironment.INDOOR -> ParcelType.WINDOWSILL
        }
        var selectedType by remember { mutableStateOf(defaultType) }
        var selectedSize by remember { mutableStateOf(3) }
        var containerFormat by remember { mutableStateOf(1 to 3) }
        var containerVolume by remember { mutableStateOf(20) }

        val nameContainer = stringResource(Res.string.garden_name_container)
        val nameWindowsill = stringResource(Res.string.garden_name_windowsill)
        val nameShelf = stringResource(Res.string.garden_name_shelf)
        val nameParcel = stringResource(Res.string.garden_name_parcel)
        val nameSquare = stringResource(Res.string.garden_name_square)

        AlertDialog(
            onDismissRequest = { showAddParcelDialog = false },
            title = {
                Text(
                    text = stringResource(Res.string.garden_new_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(Res.string.garden_new_type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ParcelType.entries) { type ->
                            val selected = selectedType == type
                            Surface(
                                modifier = Modifier.clickable { selectedType = type },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                ),
                            ) {
                                Text(
                                    text = type.frenchLabel,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }

                    if (selectedType.isContainerBased) {
                        val presets = listOf(
                            Triple(1 to 1, "1 × 1", stringResource(Res.string.garden_preset_pot)),
                            Triple(1 to 3, "1 × 3", stringResource(Res.string.garden_preset_planter)),
                            Triple(2 to 3, "2 × 3", stringResource(Res.string.garden_preset_bin)),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            presets.forEach { (dims, title, desc) ->
                                val selected = containerFormat == dims
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { containerFormat = dims },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(text = title, fontWeight = FontWeight.Bold)
                                        Text(text = desc, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        SillonStepper(
                            value = containerVolume,
                            onValueChange = { containerVolume = it },
                            min = 2,
                            max = 200,
                            label = stringResource(Res.string.garden_volume_label),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.garden_standard_format),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedSize = 3 },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedSize == 3) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                                contentColor = if (selectedSize == 3) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground,
                                border = BorderStroke(
                                    1.5.dp,
                                    if (selectedSize == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(text = "3 × 3", fontWeight = FontWeight.Bold)
                                    Text(text = stringResource(Res.string.garden_size_3), style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedSize = 4 },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedSize == 4) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                                contentColor = if (selectedSize == 4) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground,
                                border = BorderStroke(
                                    1.5.dp,
                                    if (selectedSize == 4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(text = "4 × 4", fontWeight = FontWeight.Bold)
                                    Text(text = stringResource(Res.string.garden_size_4), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val idx = garden.parcels.size + 1
                    if (selectedType.isContainerBased) {
                        val prefix = when (selectedType) {
                            ParcelType.CONTAINER -> nameContainer
                            ParcelType.WINDOWSILL -> nameWindowsill
                            ParcelType.GROW_LIGHT_SHELF -> nameShelf
                            else -> nameParcel
                        }
                        onAddParcelWithVolume(
                            "$prefix $idx",
                            selectedType,
                            containerFormat.first,
                            containerFormat.second,
                            containerVolume,
                        )
                    } else {
                        onAddParcelWithVolume(
                            "$nameSquare $idx",
                            selectedType,
                            selectedSize,
                            selectedSize,
                            null,
                        )
                    }
                    showAddParcelDialog = false
                }) {
                    Text(stringResource(Res.string.garden_create), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddParcelDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun ParcelGridView(
    parcel: Parcel,
    vegetables: List<Vegetable>,
    journal: List<DiagnosticJournalEntry>,
    onSlotClick: (row: Int, col: Int) -> Unit,
) {
    val today = remember { todayLocalDate() }
    SillonCard(
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (r in 0 until parcel.rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (c in 0 until parcel.cols) {
                        val slot = parcel.getSlot(r, c)
                        val veg = vegetables.find { it.id == slot?.vegetableId }

                        // Synergies et alertes avec les voisins directs, lues dans les deux sens
                        val neighborVegs = orthogonalNeighbors(parcel, vegetables, r, c)
                        val hasGoodCompanion = veg != null && neighborVegs.any { neighbor ->
                            veg.goodRelationsWith(neighbor).isNotEmpty()
                        }
                        val hasBadCompanion = veg != null && neighborVegs.any { neighbor ->
                            veg.badRelationsWith(neighbor).isNotEmpty()
                        }
                        val neighborNote = if (veg != null) neighborSpokenNoteRes(veg, neighborVegs)?.let { stringResource(it) } else null

                        val openProblem = journal.firstOrNull {
                            it.isOpen && it.parcelId == parcel.id && it.row == r && it.col == c && it.concerns(slot)
                        }
                        val problemLabel = openProblem?.let { entry ->
                            entry.diseaseId?.let { DiseaseCatalog.getById(it)?.name } ?: stringResource(Res.string.journal_cause_uncertain)
                        }

                        SlotCell(
                            vegetable = veg,
                            problemLabel = problemLabel,
                            growth = slotGrowth(slot, veg, today),
                            hasGoodCompanion = hasGoodCompanion,
                            hasBadCompanion = hasBadCompanion,
                            neighborNote = neighborNote,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            onClick = { onSlotClick(r, c) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotCell(
    vegetable: Vegetable?,
    problemLabel: String?,
    growth: Growth?,
    hasGoodCompanion: Boolean,
    hasBadCompanion: Boolean,
    neighborNote: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Un seul énoncé pour les lecteurs d'écran : nom, stade et délai, sans dépendre du pictogramme.
    val description = when {
        vegetable == null -> stringResource(Res.string.garden_slot_free)
        growth != null -> "${vegetable.name}. ${growth.summary()}"
        else -> vegetable.name
    }.let { base -> if (neighborNote != null) "$base. $neighborNote" else base }
        .let { base -> if (problemLabel != null) "$base. ${stringResource(Res.string.garden_slot_problem_open, problemLabel)}" else base }
    Surface(
        modifier = modifier
            .clickable(onClickLabel = stringResource(Res.string.garden_slot_open), onClick = onClick)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = if (vegetable != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            2.dp,
            when {
                hasBadCompanion -> SillonTheme.colors.alert
                hasGoodCompanion -> MaterialTheme.colorScheme.primary
                vegetable != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                // L'énoncé de la case suffit : on évite la lecture du pictogramme puis du nom en double.
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            // Badge d'affinité en haut à droite
            if (hasGoodCompanion) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            } else if (hasBadCompanion) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SillonTheme.colors.alert),
                )
            }

            // Marque discrète d'un problème en cours ; le texte de la case le dit aussi, l'icône ne porte rien seule.
            if (problemLabel != null) {
                Icon(
                    imageVector = Icons.Filled.Healing,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.TopStart).size(12.dp),
                )
            }

            if (vegetable != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    StageVegetableVisual(
                        vegetable = vegetable,
                        growth = growth,
                        iconSize = 26.dp,
                        emojiSize = if (growth?.stage == GrowthStage.SETTLING) 16.sp else 22.sp,
                    )
                    Text(
                        text = vegetable.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    if (growth == null) {
                        Text(
                            text = "×${vegetable.squareFootDensity}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else if (growth.stage == GrowthStage.READY || growth.stage == GrowthStage.FINISHED) {
                        // Le mot double la couleur : « À récolter » ne se lit pas seulement à la teinte.
                        Text(
                            text = stringResource(if (growth.stage == GrowthStage.READY) Res.string.garden_stage_ready else Res.string.garden_stage_finished),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = if (growth.stage == GrowthStage.READY) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        GrowthBar(growth = growth, height = 5, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.garden_slot_add),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Stade de la culture d'une case, si elle a une plante connue et une date de mise en place. */
private fun slotGrowth(slot: ParcelSlot?, vegetable: Vegetable?, today: LocalDate): Growth? {
    val planted = slot?.plantedDate ?: return null
    if (vegetable == null) return null
    val plantedDate = epochMillisToLocalDate(planted)
    val days = daysBetween(plantedDate, today).toInt()
    return GrowthEngine.growth(days, vegetable.daysToHarvest, vegetable.harvestDurationWeeks)
}

/** La plante « pousse » : pousse, feuillage, puis l'icône vectorielle du légume quand la récolte approche. */
@Composable
private fun StageVegetableVisual(
    vegetable: Vegetable,
    growth: Growth?,
    iconSize: Dp,
    emojiSize: androidx.compose.ui.unit.TextUnit,
) {
    when (growth?.stage) {
        GrowthStage.SETTLING, GrowthStage.YOUNG -> Text(text = "\uD83C\uDF31", fontSize = emojiSize)
        GrowthStage.GROWING -> Text(text = "\uD83C\uDF3F", fontSize = emojiSize)
        GrowthStage.FINISHED -> Text(text = "\uD83C\uDF42", fontSize = emojiSize)
        GrowthStage.ALMOST_READY, GrowthStage.READY, null ->
            VegetableIcon(vegetable = vegetable, modifier = Modifier.size(iconSize))
    }
}

/** Jauge d'avancement vers la récolte. Décorative : le délai est toujours écrit à côté ou énoncé. */
@Composable
private fun GrowthBar(growth: Growth, height: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.outline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(growth.progress.coerceIn(0.04f, 1f))
                .height(height.dp)
                .clip(RoundedCornerShape(50))
                .background(if (growth.stage == GrowthStage.READY) MaterialTheme.colorScheme.secondary else SillonTheme.colors.sprout),
        )
    }
}

@Composable
private fun ViewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) SillonPalette.SageContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) SillonPalette.Sage else MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) SillonPalette.SageDark else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

/** Les affinités et alertes réellement détectées dans la parcelle, en phrases courtes. */
@Composable
private fun NeighborhoodCard(parcel: Parcel, vegetables: List<Vegetable>) {
    val links = remember(parcel, vegetables) { neighborLinks(parcel, vegetables) }
    SillonCard(
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.garden_neighborhood),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (links.isEmpty()) {
                Text(
                    text = stringResource(Res.string.garden_neighborhood_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (link in links.take(6)) {
                val second = link.second.name.replaceFirstChar { it.lowercase() }
                val sentence = stringResource(link.sentenceRes(), link.first.name, second)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (link.positive) SillonPalette.Sage else SillonPalette.AlertAmber),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = sentence,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // Le niveau de preuve est écrit : la couleur de la pastille ne porte pas seule le sens.
                        Text(
                            text = stringResource(evidenceLabelRes(link.evidence)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (links.size > 6) {
                Text(
                    text = pluralStringResource(Res.plurals.garden_links_more, links.size - 6, links.size - 6),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (links.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.evidence_explanation),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Vue affichée : la perspective (parcelIndex fixe à -1, la scène anime elle-même le changement de carré) ou la vue à plat d'un carré. */
private data class GardenViewKey(val perspective: Boolean, val parcelIndex: Int)

private const val VIEW_SWITCH_MS = 260

/** Glissement court (un dixième de la largeur) avec fondu ; instantané si [reduceMotion]. */
private fun AnimatedContentTransitionScope<GardenViewKey>.gardenViewTransition(reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) return (EnterTransition.None togetherWith ExitTransition.None).using(null)
    val forward = if (initialState.perspective != targetState.perspective) {
        !targetState.perspective // perspective -> à plat : vers la droite
    } else {
        targetState.parcelIndex >= initialState.parcelIndex
    }
    val sign = if (forward) 1 else -1
    return (
        fadeIn(tween(VIEW_SWITCH_MS)) + slideInHorizontally(tween(VIEW_SWITCH_MS)) { sign * it / 10 }
        ) togetherWith (
        fadeOut(tween(VIEW_SWITCH_MS)) + slideOutHorizontally(tween(VIEW_SWITCH_MS)) { -sign * it / 10 }
        ) using SizeTransform(clip = true)
}

/** Case à ouvrir en arrivant sur le potager (depuis l'accueil ou un rappel). */
data class GardenFocus(val parcelId: String, val row: Int, val col: Int)

/**
 * Journal d'un emplacement : avertissement de rotation lié aux maladies persistantes, puis les problèmes notés,
 * du plus récent au plus ancien. Un problème ouvert propose « Ça va mieux ? », sans insistance : la question
 * reste là, rien ne relance la personne.
 */
@Composable
private fun SlotJournal(
    garden: Garden,
    parcelId: String,
    row: Int,
    col: Int,
    today: LocalDate,
    onAnswer: (entryId: String, answer: FollowUpAnswer) -> Unit,
) {
    val warnings = remember(garden.diagnosticJournal, parcelId, row, col, today.year) {
        RotationEngine().soilWarnings(parcelId, row, col, today.year, garden.diagnosticJournal)
    }
    val entries = remember(garden.diagnosticJournal, parcelId, row, col) {
        DiagnosticJournal.forSlot(garden.diagnosticJournal, parcelId, row, col)
    }
    warnings.forEach { warning ->
        val examples = VegetableCatalog.ALL_VEGETABLES
            .filter { it.family in warning.families && it.climates.contains(Climate.TEMPERATE) }
            .take(3)
            .joinToString(", ") { it.name.lowercase() }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(
                Res.string.rotation_soil_warning,
                warning.disease.name,
                warning.notedYear,
                warning.families.joinToString(", ") { it.frenchLabel },
                examples,
                warning.untilYear,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = SillonPalette.AlertAmber,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (entries.isEmpty()) return
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(Res.string.journal_section_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    entries.take(MAX_JOURNAL_SHOWN).forEach { entry ->
        Spacer(modifier = Modifier.height(8.dp))
        JournalEntryCard(entry = entry, today = today, onAnswer = onAnswer)
    }
}

private const val MAX_JOURNAL_SHOWN = 5

@Composable
private fun JournalEntryCard(
    entry: DiagnosticJournalEntry,
    today: LocalDate,
    onAnswer: (entryId: String, answer: FollowUpAnswer) -> Unit,
) {
    val cause = entry.diseaseId?.let { DiseaseCatalog.getById(it)?.name } ?: stringResource(Res.string.journal_cause_uncertain)
    val status = stringResource(
        when (entry.status) {
            JournalStatus.OPEN -> Res.string.journal_status_open
            JournalStatus.BETTER -> Res.string.journal_status_better
            JournalStatus.LOST -> Res.string.journal_status_lost
            JournalStatus.ENDED -> Res.string.journal_status_ended
        },
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(
                    Res.string.journal_entry_line,
                    cause,
                    VegetableCatalog.getById(entry.vegetableId)?.name ?: entry.vegetableId,
                    formatDayMonth(entry.noticedOn),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(text = status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            entry.action?.let {
                Text(
                    text = stringResource(Res.string.journal_action_line, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.isOpen) {
                entry.followUpOn?.let { due ->
                    Text(
                        text = if (due <= today) {
                            stringResource(Res.string.journal_followup_due)
                        } else {
                            stringResource(Res.string.journal_followup_on, formatDayMonth(due))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.journal_ask),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onAnswer(entry.id, FollowUpAnswer.BETTER) }) { Text(stringResource(Res.string.journal_yes)) }
                    TextButton(onClick = { onAnswer(entry.id, FollowUpAnswer.NOT_BETTER) }) { Text(stringResource(Res.string.journal_no)) }
                    TextButton(onClick = { onAnswer(entry.id, FollowUpAnswer.LOST) }) { Text(stringResource(Res.string.journal_lost)) }
                }
            }
        }
    }
}
