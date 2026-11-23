@file:OptIn(ExperimentalResourceApi::class)

package fr.pilmil.sillon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.data.FrenchFrostCatalog
import fr.pilmil.sillon.data.GardenLoad
import fr.pilmil.sillon.data.GardenStore
import fr.pilmil.sillon.data.GardenUpdate
import fr.pilmil.sillon.data.VegetableCatalog
import fr.pilmil.sillon.data.rememberGardenStore
import fr.pilmil.sillon.data.rememberSillonPrefs
import fr.pilmil.sillon.engine.DashboardPlanner
import fr.pilmil.sillon.engine.DiagnosticContext
import fr.pilmil.sillon.engine.DiagnosticJournal
import fr.pilmil.sillon.engine.FrostEngine
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.model.Climate
import fr.pilmil.sillon.model.Garden
import fr.pilmil.sillon.model.plusDays
import fr.pilmil.sillon.model.todayLocalDate
import fr.pilmil.sillon.shared.resources.*
import fr.pilmil.sillon.ui.catalog.CatalogScreen
import fr.pilmil.sillon.ui.dashboard.DashboardScreen
import fr.pilmil.sillon.ui.dashboard.ForecastHorizon
import fr.pilmil.sillon.ui.doctor.DoctorScreen
import fr.pilmil.sillon.ui.garden.GardenFocus
import fr.pilmil.sillon.ui.garden.GardenScreen
import fr.pilmil.sillon.ui.onboarding.DepartmentDialog
import fr.pilmil.sillon.ui.onboarding.OnboardingScreen
import fr.pilmil.sillon.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

enum class SillonScreen(val title: StringResource, val icon: ImageVector) {
    DASHBOARD(Res.string.nav_dashboard, Icons.Filled.CalendarMonth),
    GARDEN(Res.string.nav_garden, Icons.Filled.Yard),
    CATALOG(Res.string.nav_catalog, Icons.Filled.Eco),
    DOCTOR(Res.string.nav_doctor, Icons.Filled.Healing),
    SETTINGS(Res.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun SillonApp(
    externalGarden: Garden? = null,
    onGardenReplaced: (Garden) -> Unit = {},
    photoDiagnosisContent: (@Composable (onOpenDisease: (String) -> Unit, onOpenKey: () -> Unit, plantFamily: BotanicalFamily?) -> Unit)? = null,
    onApplyReminders: (enabled: Boolean, garden: Garden) -> Unit = { _, _ -> },
    /** Recale les rappels « Revérifier » sur les entrées ouvertes du journal (appelée à chaque changement du journal). */
    onSyncFollowUps: (Garden) -> Unit = {},
    cloudBackupAvailable: Boolean = false,
    cloudBackupPossible: Boolean = false,
    cloudBackupOn: Boolean = false,
    onCloudBackupChange: (Boolean) -> Unit = {},
    onExportJson: ((Garden) -> Unit)? = null,
    onImportJson: ((onLoaded: (Garden) -> Unit) -> Unit)? = null,
    onExportPilmil: ((CharArray) -> Unit)? = null,
    onImportPilmil: ((CharArray, onLoaded: (Garden) -> Unit) -> Unit)? = null,
    onExportCsv: ((GardenStore) -> Unit)? = null,
) {
    val prefs = rememberSillonPrefs()
    val store = rememberGardenStore()
    val scope = rememberCoroutineScope()
    val storeMutex = remember { Mutex() }
    val snackbarHostState = remember { SnackbarHostState() }

    var currentScreen by rememberSaveable { mutableStateOf(SillonScreen.DASHBOARD) }
    var loadedGarden by remember { mutableStateOf<Garden?>(null) }
    var onboarded by remember { mutableStateOf(prefs.onboarded) }
    var departmentCode by remember { mutableStateOf(prefs.departmentCode) }
    var remindersOn by remember { mutableStateOf(prefs.sowingReminders) }
    var smartViewfinderOn by remember { mutableStateOf(prefs.smartViewfinderEnabled) }
    var pickingDepartment by remember { mutableStateOf(false) }
    var loadProblem by remember { mutableStateOf<GardenLoad?>(null) }
    // Plante dont on diagnostique le problème (null : écran docteur autonome) et case à ouvrir au retour au potager.
    var doctorContext by remember { mutableStateOf<DiagnosticContext?>(null) }
    var gardenFocus by remember { mutableStateOf<GardenFocus?>(null) }

    val showIntroHints = remember { prefs.dashboardVisits < 3 }

    fun loadFromDisk() {
        scope.launch {
            val (result, recovered) = withContext(Dispatchers.IO) {
                storeMutex.withLock {
                    store.load() to store.consumeRecoveryNotice()
                }
            }
            if (result is GardenLoad.Ready) {
                loadProblem = null
                loadedGarden = result.garden
            } else {
                loadProblem = result
            }
            if (recovered) {
                snackbarHostState.showSnackbar(getString(Res.string.snack_recovered))
            }
        }
    }

    LaunchedEffect(Unit) {
        prefs.dashboardVisits = prefs.dashboardVisits + 1
        loadFromDisk()
    }

    LaunchedEffect(externalGarden) {
        if (externalGarden != null) {
            // Le fichier vient d'être remplacé par l'import : la mémoire du magasin doit suivre.
            storeMutex.withLock { store.adopt(externalGarden) }
            loadProblem = null
            loadedGarden = externalGarden
        }
    }

    fun update(change: () -> GardenUpdate) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                storeMutex.withLock { change() }
            }
            if (result.refused) {
                snackbarHostState.showSnackbar(getString(Res.string.snack_load_refused))
                return@launch
            }
            loadedGarden = result.garden
            onGardenReplaced(result.garden)
            if (!result.saved) {
                snackbarHostState.showSnackbar(getString(Res.string.snack_save_failed))
            }
        }
    }

    // Les rappels suivent le journal : au chargement, après chaque modification et après une importation.
    LaunchedEffect(loadedGarden?.diagnosticJournal) {
        loadedGarden?.let(onSyncFollowUps)
    }

    val followUpPermissionRequester = rememberNotificationPermissionRequester { granted ->
        if (!granted) {
            scope.launch { snackbarHostState.showSnackbar(getString(Res.string.followup_notifications_off)) }
        }
    }

    fun navigate(screen: SillonScreen) {
        // Un changement d'onglet quitte le diagnostic d'une plante : l'onglet Docteur redevient l'outil autonome.
        if (screen != currentScreen) doctorContext = null
        currentScreen = screen
    }

    fun chooseDepartment(code: String?) {
        prefs.departmentCode = code
        departmentCode = code
        val dep = code?.let(FrenchFrostCatalog::getByCode) ?: return
        update {
            store.updateFrostDate(dep.medianLastFrost.month, dep.medianLastFrost.day)
            // Choisir un département quitte le mode « mes dates », sans effacer celles qui ont été saisies.
            store.setUseCustomFrost(false)
        }
    }

    fun setReminders(on: Boolean, garden: Garden) {
        prefs.sowingReminders = on
        remindersOn = on
        onApplyReminders(on, garden)
    }

    val reminderPermissionRequester = rememberReminderPermissionRequester { granted ->
        val g = loadedGarden ?: return@rememberReminderPermissionRequester
        setReminders(granted, g)
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar(getString(Res.string.snack_notifications_denied))
            }
        }
    }

    val garden: Garden = loadedGarden ?: run {
        val problem = loadProblem
        if (problem == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            GardenLoadProblemScreen(problem, onRetry = { loadFromDisk() })
        }
        return
    }

    if (!onboarded) {
        OnboardingScreen(
            onDepartment = { chooseDepartment(it?.code) },
            onCustomFrost = { month, day -> update { store.updateCustomFrost(month, day) } },
            onEnvironment = { env ->
                update { store.updateEnvironment(environment = env) }
            },
            onFinish = { reminders ->
                setReminders(reminders, garden)
                prefs.onboarded = true
                onboarded = true
            },
        )
        return
    }

    if (pickingDepartment) {
        DepartmentDialog(
            selectedCode = departmentCode,
            onPick = {
                chooseDepartment(it.code)
                pickingDepartment = false
            },
            onDismiss = { pickingDepartment = false },
        )
    }

    // Les dates saisies sont actives seulement si elles existent : sans date, c'est le département qui sert.
    val customFrostActive = garden.useCustomFrost && garden.customFrostDate != null

    PlatformBackHandler(enabled = currentScreen != SillonScreen.DASHBOARD) {
        // Depuis le diagnostic d'une plante, « retour » ramène au potager ; sinon, à l'accueil.
        if (currentScreen == SillonScreen.DOCTOR && doctorContext != null) {
            doctorContext = null
            currentScreen = SillonScreen.GARDEN
        } else {
            doctorContext = null
            currentScreen = SillonScreen.DASHBOARD
        }
    }

    val diseases = DiseaseCatalog.ALL_DISEASES
    val vegetables = VegetableCatalog.ALL_VEGETABLES

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            SillonBottomNavigation(
                currentScreen = currentScreen,
                onSelectScreen = { navigate(it) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                SillonScreen.DASHBOARD -> {
                    val today = remember { todayLocalDate() }
                    val climate = remember(departmentCode, garden.useCustomFrost, garden.customFrostDate) {
                        garden.climateFor(FrenchFrostCatalog.getClimate(departmentCode))
                    }
                    val frostEngine = remember(climate) { FrostEngine(climate = climate) }
                    val horizonToday = stringResource(Res.string.horizon_today)
                    val horizonTodayHint = stringResource(Res.string.horizon_today_hint)
                    val horizonNextDays = stringResource(Res.string.horizon_next_days)
                    val horizonNextWeek = stringResource(Res.string.horizon_next_week)
                    val horizonUpcoming = stringResource(Res.string.horizon_upcoming)
                    val hint3 = stringResource(Res.string.horizon_hint_days, 3)
                    val hint7 = stringResource(Res.string.horizon_hint_days, 7)
                    val hint15 = stringResource(Res.string.horizon_hint_days, 15)
                    val horizons = remember(garden, today, frostEngine, horizonToday) {
                        listOf(
                            ForecastHorizon(horizonToday, horizonTodayHint, DashboardPlanner.plan(garden, today, frostEngine)),
                            ForecastHorizon(horizonNextDays, hint3, DashboardPlanner.plan(garden, today.plusDays(3), frostEngine)),
                            ForecastHorizon(horizonNextWeek, hint7, DashboardPlanner.plan(garden, today.plusDays(7), frostEngine)),
                            ForecastHorizon(horizonUpcoming, hint15, DashboardPlanner.plan(garden, today.plusDays(15), frostEngine)),
                        )
                    }
                    val activeCropIds = remember(garden) {
                        garden.parcels.flatMap { it.slots }.mapNotNull { it.vegetableId }.toSet()
                    }
                    val parcelCount = garden.parcels.size
                    val plantedCount = garden.parcels.sumOf { p -> p.slots.count { it.vegetableId != null } }
                    val parcelsReady = pluralStringResource(Res.plurals.parcels_ready, parcelCount, parcelCount)
                    val parcelsCount = pluralStringResource(Res.plurals.parcels_count, parcelCount, parcelCount)
                    val cropsInPlace = pluralStringResource(Res.plurals.crops_in_place, plantedCount, plantedCount)
                    val activeGardenMarker = when {
                        parcelCount == 0 -> null
                        plantedCount == 0 -> parcelsReady
                        else -> "$parcelsCount · $cropsInPlace"
                    }
                    val tipOnPlots = stringResource(Res.string.tip_on_plots)
                    val tipLandmark = stringResource(Res.string.tip_landmark)
                    val tip = remember(today, activeCropIds, tipOnPlots, tipLandmark) {
                        val plantedAdvice = vegetables.filter { it.id in activeCropIds && it.advice.isNotBlank() }
                        val pool = plantedAdvice.ifEmpty {
                            vegetables.filter { it.advice.isNotBlank() }
                        }
                        pool.getOrNull(today.dayOfYear % pool.size.coerceAtLeast(1))?.let {
                            "${if (plantedAdvice.isNotEmpty()) tipOnPlots else tipLandmark} · ${it.name}" to it.advice
                        }
                    }
                    val followUps = remember(garden, today) { DiagnosticJournal.due(garden, today) }
                    DashboardScreen(
                        horizons = horizons,
                        placeLabel = if (customFrostActive) {
                            stringResource(Res.string.custom_frost_place)
                        } else {
                            departmentCode?.let(FrenchFrostCatalog::getByCode)?.let { "${it.name} (${it.code})" }
                        },
                        tip = tip,
                        showIntroHints = showIntroHints,
                        activeGardenMarker = activeGardenMarker,
                        followUps = followUps,
                        onOpenFollowUp = { entry ->
                            gardenFocus = GardenFocus(entry.parcelId, entry.row, entry.col)
                            navigate(SillonScreen.GARDEN)
                        },
                        onNavigateToGarden = { navigate(SillonScreen.GARDEN) },
                        onNavigateToCatalog = { navigate(SillonScreen.CATALOG) },
                        onOpenSettings = { pickingDepartment = true },
                    )
                }

                SillonScreen.GARDEN -> GardenScreen(
                    garden = garden,
                    vegetables = vegetables,
                    departmentCode = departmentCode,
                    onUpdateSlot = { pId, r, c, vId ->
                        update { store.updateSlot(pId, r, c, vId) }
                    },
                    onAddParcel = { name, type, r, c ->
                        update { store.addParcel(name, type, r, c) }
                    },
                    onAddParcelWithVolume = { name, type, r, c, vol ->
                        update { store.addParcel(name, type, r, c, vol) }
                    },
                    onDeleteParcel = { pId ->
                        update { store.deleteParcel(pId) }
                    },
                    onReportProblem = { pId, r, c ->
                        val parcel = garden.parcels.firstOrNull { it.id == pId }
                        val slot = parcel?.getSlot(r, c)
                        val vegetable = slot?.vegetableId?.let(VegetableCatalog::getById)
                        if (parcel != null && slot != null && vegetable != null) {
                            doctorContext = DiagnosticContext.from(
                                parcel = parcel,
                                slot = slot,
                                vegetable = vegetable,
                                today = todayLocalDate(),
                                temperateClimate = garden.climateFor(FrenchFrostCatalog.getClimate(departmentCode)) == Climate.TEMPERATE,
                            )
                            currentScreen = SillonScreen.DOCTOR
                        }
                    },
                    onAnswerFollowUp = { entryId, answer ->
                        update { store.answerJournalEntry(entryId, answer) }
                    },
                    focus = gardenFocus,
                    onFocusConsumed = { gardenFocus = null },
                )

                SillonScreen.CATALOG -> CatalogScreen(
                    vegetables = vegetables,
                )

                SillonScreen.DOCTOR -> DoctorScreen(
                    diseases = diseases,
                    photoDiagnosisContent = photoDiagnosisContent,
                    context = doctorContext,
                    onBackToGarden = {
                        // Retour au potager, sans rouvrir la case : la personne reprend où elle en était.
                        if (doctorContext != null) {
                            doctorContext = null
                            currentScreen = SillonScreen.GARDEN
                        }
                    },
                    onNoteProblem = { draft -> update { store.addJournalEntry(draft) } },
                    onFollowUp = { entryId, days ->
                        update { store.setJournalFollowUp(entryId, days) }
                        if (days != null) followUpPermissionRequester()
                    },
                )

                SillonScreen.SETTINGS -> SettingsScreen(
                    frostMonth = garden.activeFrostDate.month,
                    frostDay = garden.activeFrostDate.day,
                    climate = garden.climateFor(FrenchFrostCatalog.getClimate(departmentCode)),
                    altitudeMeters = garden.altitudeMeters,
                    onAltitudeChange = { meters ->
                        update { store.updateAltitude(meters) }
                    },
                    mulched = garden.mulched,
                    localWeather = garden.localWeather,
                    onWateringConditionsChange = { mulched, weather ->
                        update { store.updateWateringConditions(mulched, weather) }
                    },
                    onFrostDateChange = { m, d ->
                        val day = d.coerceAtMost(daysInMonth(m))
                        update { store.updateFrostDate(m, day) }
                        prefs.departmentCode = null
                        departmentCode = null
                    },
                    environment = garden.environment,
                    orientation = garden.orientation,
                    windExposure = garden.windExposure,
                    covered = garden.covered,
                    lightSource = garden.lightSource,
                    growLightHoursPerDay = garden.growLightHoursPerDay,
                    onEnvironmentChange = { env, ori, wind, cov, src, hours ->
                        update { store.updateEnvironment(env, ori, wind, cov, src, hours) }
                    },
                    departmentLabel = departmentCode?.takeUnless { customFrostActive }
                        ?.let(FrenchFrostCatalog::getByCode)?.let { "${it.name} (${it.code})" },
                    onPickDepartment = { pickingDepartment = true },
                    customFrostActive = customFrostActive,
                    onCustomFrost = { month, day -> update { store.updateCustomFrost(month, day) } },
                    remindersOn = remindersOn,
                    onRemindersChange = { on ->
                        if (on) {
                            reminderPermissionRequester()
                        } else {
                            setReminders(false, garden)
                        }
                    },
                    cloudBackupAvailable = cloudBackupAvailable,
                    cloudBackupPossible = cloudBackupPossible,
                    cloudBackupOn = cloudBackupOn,
                    onCloudBackupChange = onCloudBackupChange,
                    smartViewfinderOn = smartViewfinderOn,
                    onSmartViewfinderChange = { on ->
                        prefs.smartViewfinderEnabled = on
                        smartViewfinderOn = on
                    },
                    onExportJson = { onExportJson?.invoke(garden) },
                    onImportJson = {
                        onImportJson?.invoke { imported ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    storeMutex.withLock { store.saveGarden(imported) }
                                }
                                loadedGarden = imported
                            }
                        }
                    },
                    onExportPilmil = onExportPilmil,
                    onImportPilmil = if (onImportPilmil != null) {
                        { passphrase ->
                            onImportPilmil.invoke(passphrase) { imported ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        storeMutex.withLock { store.saveGarden(imported) }
                                    }
                                    loadedGarden = imported
                                }
                            }
                        }
                    } else {
                        null
                    },
                    onExportCsv = { onExportCsv?.invoke(store) },
                )
            }
        }
    }
}

private fun daysInMonth(month: Int): Int = when (month) {
    2 -> 28
    4, 6, 9, 11 -> 30
    else -> 31
}

@Composable
private fun SillonBottomNavigation(
    currentScreen: SillonScreen,
    onSelectScreen: (SillonScreen) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        val navColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SillonScreen.entries.forEach { screen ->
            NavigationBarItem(
                selected = currentScreen == screen,
                onClick = { onSelectScreen(screen) },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = stringResource(screen.title),
                    )
                },
                label = {
                    Text(
                        text = stringResource(screen.title),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                alwaysShowLabel = true,
                colors = navColors,
            )
        }
    }
}

/** Le potager n'a pas pu être chargé : on n'affiche rien d'inventé et on ne modifie rien. */
@Composable
private fun GardenLoadProblemScreen(problem: GardenLoad, onRetry: () -> Unit) {
    val message = when (problem) {
        is GardenLoad.NewerVersion ->
            stringResource(Res.string.load_newer)
        else ->
            stringResource(Res.string.load_failed)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}
