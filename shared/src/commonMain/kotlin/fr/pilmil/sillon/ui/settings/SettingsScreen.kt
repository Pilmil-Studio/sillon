package fr.pilmil.sillon.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pilmil.sillon.engine.LightEstimator
import fr.pilmil.sillon.model.Climate
import fr.pilmil.sillon.model.GrowingEnvironment
import fr.pilmil.sillon.model.LightSource
import fr.pilmil.sillon.model.LocalWeather
import fr.pilmil.sillon.model.Orientation
import fr.pilmil.sillon.model.WindExposure
import fr.pilmil.sillon.shared.resources.*
import fr.pilmil.sillon.ui.components.SillonBadge
import fr.pilmil.sillon.ui.components.SillonButton
import fr.pilmil.sillon.ui.components.SillonCard
import fr.pilmil.sillon.ui.components.SillonOutlinedButton
import fr.pilmil.sillon.ui.components.SillonStepper
import fr.pilmil.sillon.ui.rememberAmbientLuxSensor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    frostMonth: Int = 5,
    frostDay: Int = 15,
    climate: Climate = Climate.TEMPERATE,
    altitudeMeters: Int = 0,
    onAltitudeChange: (Int) -> Unit = {},
    mulched: Boolean = false,
    localWeather: LocalWeather = LocalWeather.NORMAL,
    onWateringConditionsChange: (Boolean, LocalWeather) -> Unit = { _, _ -> },
    onFrostDateChange: (Int, Int) -> Unit = { _, _ -> },
    environment: GrowingEnvironment = GrowingEnvironment.OUTDOOR,
    orientation: Orientation? = null,
    windExposure: WindExposure? = null,
    covered: Boolean = false,
    lightSource: LightSource? = null,
    growLightHoursPerDay: Int? = null,
    onEnvironmentChange: (
        environment: GrowingEnvironment,
        orientation: Orientation?,
        windExposure: WindExposure?,
        covered: Boolean,
        lightSource: LightSource?,
        growLightHoursPerDay: Int?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onExportJson: () -> Unit = {},
    onImportJson: () -> Unit = {},
    onExportPilmil: ((CharArray) -> Unit)? = null,
    onImportPilmil: ((CharArray) -> Unit)? = null,
    onExportCsv: () -> Unit = {},
    departmentLabel: String? = null,
    onPickDepartment: () -> Unit = {},
    remindersOn: Boolean = false,
    onRemindersChange: (Boolean) -> Unit = {},
    cloudBackupAvailable: Boolean = false,
    cloudBackupPossible: Boolean = false,
    cloudBackupOn: Boolean = false,
    onCloudBackupChange: (Boolean) -> Unit = {},
    smartViewfinderOn: Boolean = true,
    onSmartViewfinderChange: (Boolean) -> Unit = {},
) {
    val monthNames = stringArrayResource(Res.array.month_names)
    var pilmilDialogMode by remember { mutableStateOf<String?>(null) }
    var passphraseInput by remember { mutableStateOf("") }

    if (pilmilDialogMode != null) {
        val isExport = pilmilDialogMode == "export"
        AlertDialog(
            onDismissRequest = {
                pilmilDialogMode = null
                passphraseInput = ""
            },
            title = { Text(stringResource(if (isExport) Res.string.dialog_export_title else Res.string.dialog_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(if (isExport) Res.string.dialog_export_text else Res.string.dialog_import_text),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text(stringResource(Res.string.passphrase)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passphraseInput.isNotEmpty(),
                    onClick = {
                        val chars = passphraseInput.toCharArray()
                        pilmilDialogMode = null
                        passphraseInput = ""
                        if (isExport) onExportPilmil?.invoke(chars) else onImportPilmil?.invoke(chars)
                    },
                ) {
                    Text(stringResource(if (isExport) Res.string.action_export else Res.string.action_import))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pilmilDialogMode = null
                    passphraseInput = ""
                }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Titre
        item {
            Column {
                Text(
                    text = stringResource(Res.string.settings_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Section Environnement de culture (Pleine terre / Balcon / Intérieur)
        item {
            SillonCard(
                backgroundColor = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.outline,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Eco,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(Res.string.env_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GrowingEnvironment.entries.forEach { env ->
                            val selected = environment == env
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onEnvironmentChange(env, orientation, windExposure, covered, lightSource, growLightHoursPerDay)
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                ),
                            ) {
                                Text(
                                    text = stringResource(env.labelRes()),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }

                    if (environment == GrowingEnvironment.BALCONY || (environment == GrowingEnvironment.INDOOR && lightSource != LightSource.GROW_LIGHT)) {
                        Text(
                            text = stringResource(Res.string.orientation_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Orientation.entries.forEach { dir ->
                                val selected = orientation == dir
                                Surface(
                                    modifier = Modifier.clickable {
                                        onEnvironmentChange(
                                            environment,
                                            if (selected) null else dir,
                                            windExposure,
                                            covered,
                                            lightSource,
                                            growLightHoursPerDay,
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    ),
                                ) {
                                    Text(
                                        text = stringResource(dir.labelRes()),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }

                    if (environment == GrowingEnvironment.BALCONY) {
                        Text(
                            text = stringResource(Res.string.wind_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WindExposure.entries.forEach { wind ->
                                val selected = windExposure == wind
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onEnvironmentChange(
                                                environment,
                                                orientation,
                                                if (selected) null else wind,
                                                covered,
                                                lightSource,
                                                growLightHoursPerDay,
                                            )
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    ),
                                ) {
                                    Text(
                                        text = stringResource(wind.labelRes()),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(Res.string.covered_balcony),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(Res.string.covered_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = covered,
                                onCheckedChange = {
                                    onEnvironmentChange(environment, orientation, windExposure, it, lightSource, growLightHoursPerDay)
                                },
                            )
                        }
                    }

                    if (environment == GrowingEnvironment.INDOOR) {
                        Text(
                            text = stringResource(Res.string.light_source_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LightSource.entries.forEach { src ->
                                val selected = (lightSource ?: LightSource.WINDOW) == src
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onEnvironmentChange(
                                                environment,
                                                orientation,
                                                windExposure,
                                                covered,
                                                src,
                                                if (src == LightSource.GROW_LIGHT) (growLightHoursPerDay ?: 12) else null,
                                            )
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    ),
                                ) {
                                    Text(
                                        text = stringResource(src.labelRes()),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        if (lightSource == LightSource.GROW_LIGHT) {
                            SillonStepper(
                                value = growLightHoursPerDay ?: 12,
                                onValueChange = {
                                    onEnvironmentChange(environment, orientation, windExposure, covered, LightSource.GROW_LIGHT, it)
                                },
                                min = 1,
                                max = 24,
                                label = stringResource(Res.string.light_hours_label),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (environment != GrowingEnvironment.OUTDOOR) {
                        val ambientLux = rememberAmbientLuxSensor()
                        if (ambientLux != null) {
                            val category = LightEstimator.classifyLux(ambientLux)
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(Res.string.light_sensor, LightEstimator.formatSummary(ambientLux)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = category.guidance,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(Res.string.light_sensor_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Climat & Gelées
        if (environment != GrowingEnvironment.INDOOR) {
        item {
            SillonCard(
                backgroundColor = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.outline,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (climate == Climate.TROPICAL) Icons.Filled.WbSunny else Icons.Filled.AcUnit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(if (climate == Climate.TROPICAL) Res.string.season_start else Res.string.frost_ref_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            if (climate == Climate.TROPICAL) Res.string.frost_ref_tropical_text else Res.string.frost_ref_temperate_text,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SillonStepper(
                            value = frostDay,
                            onValueChange = { onFrostDateChange(frostMonth, it) },
                            min = 1,
                            max = 31,
                            label = stringResource(Res.string.day_of_month),
                            modifier = Modifier.weight(1f),
                        )
                        SillonStepper(
                            value = frostMonth,
                            onValueChange = { onFrostDateChange(it, frostDay) },
                            min = 1,
                            max = 12,
                            label = stringResource(Res.string.month_label, monthNames.getOrElse(frostMonth - 1) { "" }),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SillonOutlinedButton(
                        text = departmentLabel?.let { stringResource(Res.string.place_label, it) } ?: stringResource(Res.string.pick_department),
                        icon = Icons.Filled.Place,
                        onClick = onPickDepartment,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    SillonStepper(
                        value = (altitudeMeters / 100).coerceIn(0, 25),
                        onValueChange = { onAltitudeChange(it * 100) },
                        min = 0,
                        max = 25,
                        label = stringResource(Res.string.altitude_label, altitudeMeters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            if (climate == Climate.TROPICAL) Res.string.altitude_note_tropical else Res.string.altitude_note_temperate,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(Res.string.mulched),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Switch(
                            checked = mulched,
                            onCheckedChange = { onWateringConditionsChange(it, localWeather) },
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.weather_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LocalWeather.entries.forEach { weather ->
                            val selected = localWeather == weather
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onWateringConditionsChange(mulched, weather) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                ),
                            ) {
                                Text(
                                    text = stringResource(weather.labelRes()),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        // Section Rappels de semis
        item {
            SillonCard(
                backgroundColor = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.outline,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.reminders_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(Res.string.reminders_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = remindersOn, onCheckedChange = onRemindersChange)
                }
            }
        }

        // Section Diagnostic Photo & Viseur Intelligent
        item {
            SillonCard(
                backgroundColor = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.outline,
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CenterFocusStrong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(Res.string.viewfinder_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Switch(checked = smartViewfinderOn, onCheckedChange = onSmartViewfinderChange)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.viewfinder_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(Res.string.viewfinder_off),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Section Sauvegarde & Transfert
        item {
            SillonCard(
                backgroundColor = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.outline,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(Res.string.backup_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.backup_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    SillonButton(
                        text = stringResource(Res.string.backup_json),
                        icon = Icons.Filled.FileDownload,
                        onClick = onExportJson,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SillonOutlinedButton(
                        text = stringResource(Res.string.restore_json),
                        icon = Icons.Filled.FileUpload,
                        onClick = onImportJson,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SillonOutlinedButton(
                        text = stringResource(Res.string.export_csv),
                        icon = Icons.Filled.TableChart,
                        onClick = onExportCsv,
                    )

                    if (onExportPilmil != null && onImportPilmil != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        SillonOutlinedButton(
                            text = stringResource(Res.string.vault_export),
                            icon = Icons.Filled.Lock,
                            onClick = { pilmilDialogMode = "export" },
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SillonOutlinedButton(
                            text = stringResource(Res.string.vault_import),
                            icon = Icons.Filled.FileUpload,
                            onClick = { pilmilDialogMode = "import" },
                        )
                    }
                }
            }
        }

        // Sauvegarde dans le compte Google : désactivée par défaut, choisie par la personne
        if (cloudBackupAvailable) {
            item {
                SillonCard(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = MaterialTheme.colorScheme.outline,
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(Res.string.cloud_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = cloudBackupOn && cloudBackupPossible,
                                onCheckedChange = onCloudBackupChange,
                                enabled = cloudBackupPossible,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(if (cloudBackupPossible) Res.string.cloud_possible else Res.string.cloud_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }

        // Section Philosophie & Mentions légales Pilmil Studio
        item {
            SillonCard(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                borderColor = MaterialTheme.colorScheme.outline,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PilmilStudioMark(size = 40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(Res.string.legal_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(Res.string.legal_country),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.legal_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

private fun GrowingEnvironment.labelRes(): StringResource = when (this) {
    GrowingEnvironment.OUTDOOR -> Res.string.env_outdoor
    GrowingEnvironment.BALCONY -> Res.string.env_balcony
    GrowingEnvironment.INDOOR -> Res.string.env_indoor
}

private fun Orientation.labelRes(): StringResource = when (this) {
    Orientation.N -> Res.string.orientation_n
    Orientation.NE -> Res.string.orientation_ne
    Orientation.E -> Res.string.orientation_e
    Orientation.SE -> Res.string.orientation_se
    Orientation.S -> Res.string.orientation_s
    Orientation.SW -> Res.string.orientation_sw
    Orientation.W -> Res.string.orientation_w
    Orientation.NW -> Res.string.orientation_nw
}

private fun WindExposure.labelRes(): StringResource = when (this) {
    WindExposure.LOW -> Res.string.wind_low
    WindExposure.MEDIUM -> Res.string.wind_medium
    WindExposure.HIGH -> Res.string.wind_high
}

private fun LightSource.labelRes(): StringResource = when (this) {
    LightSource.WINDOW -> Res.string.light_window
    LightSource.GROW_LIGHT -> Res.string.light_grow
}

private fun LocalWeather.labelRes(): StringResource = when (this) {
    LocalWeather.NORMAL -> Res.string.weather_normal
    LocalWeather.DRY_HOT -> Res.string.weather_dry_hot
    LocalWeather.RAINY -> Res.string.weather_rainy
}
