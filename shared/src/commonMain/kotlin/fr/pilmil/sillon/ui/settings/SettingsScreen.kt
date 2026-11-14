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
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pilmil.sillon.engine.LightEstimator
import fr.pilmil.sillon.model.GrowingEnvironment
import fr.pilmil.sillon.model.LightSource
import fr.pilmil.sillon.model.Orientation
import fr.pilmil.sillon.model.WindExposure
import fr.pilmil.sillon.ui.components.SillonBadge
import fr.pilmil.sillon.ui.components.SillonButton
import fr.pilmil.sillon.ui.components.SillonCard
import fr.pilmil.sillon.ui.components.SillonOutlinedButton
import fr.pilmil.sillon.ui.components.SillonStepper
import fr.pilmil.sillon.ui.rememberAmbientLuxSensor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    frostMonth: Int = 5,
    frostDay: Int = 15,
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
    val monthNames = listOf(
        "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    )

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
                    text = "Réglages & Données",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Paramétrage microclimatique et souveraineté de vos données.",
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
                            text = "Environnement de culture",
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
                                    text = env.frenchLabel,
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
                            text = "Orientation",
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
                                        text = dir.name,
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
                            text = "Exposition au vent",
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
                                        text = wind.frenchLabel,
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
                                    text = "Balcon couvert",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "La pluie n'atteint pas les pots.",
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
                            text = "Source de lumière",
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
                                        text = src.frenchLabel,
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
                                label = "Durée d'éclairage (h/jour)",
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
                                    text = "Capteur de lumière : ${LightEstimator.formatSummary(ambientLux)}",
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
                                    text = "Estimation indicative à l'emplacement du téléphone (dépend de l'heure et de la météo ; mesurez à plusieurs moments de la journée).",
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
                            imageVector = Icons.Filled.AcUnit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Date repère des dernières gelées",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "En France métropolitaine, la date médiane se situe entre mi-mars (zone méditerranéenne) et mi-mai (Saints de Glace en plaine / montagne). Tous les travaux et rappels de semis sont calculés par rapport à ce jour J.",
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
                            label = "Jour du mois",
                            modifier = Modifier.weight(1f),
                        )
                        SillonStepper(
                            value = frostMonth,
                            onValueChange = { onFrostDateChange(it, frostDay) },
                            min = 1,
                            max = 12,
                            label = "Mois (${monthNames.getOrElse(frostMonth - 1) { "" }})",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SillonOutlinedButton(
                        text = departmentLabel?.let { "Département : $it" } ?: "Choisir mon département",
                        icon = Icons.Filled.Place,
                        onClick = onPickDepartment,
                    )
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
                            text = "Rappels de semis",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Le samedi matin, quand des semis ou plantations s'ouvrent dans la quinzaine.",
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
                                text = "Assistant de cadrage photo",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Switch(checked = smartViewfinderOn, onCheckedChange = onSmartViewfinderChange)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Viseur avec indicateur dynamique de netteté et d'exposition (vert/jaune/rouge) et déclenchement automatique dès que la feuille est bien cadrée.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Si désactivé, Sillon utilise l'appareil photo standard de votre téléphone sans demander de permission.",
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
                            text = "Sauvegarde & Changement de téléphone",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sillon ne requiert aucun compte et n'a aucun serveur distant. Pour sauvegarder ou migrer vos parcelles vers un nouveau smartphone, exportez votre fichier de sauvegarde en un tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    SillonButton(
                        text = "Sauvegarder mon potager (JSON)",
                        icon = Icons.Filled.FileDownload,
                        onClick = onExportJson,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SillonOutlinedButton(
                        text = "Restaurer une sauvegarde (JSON)",
                        icon = Icons.Filled.FileUpload,
                        onClick = onImportJson,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SillonOutlinedButton(
                        text = "Exporter pour tableur (CSV / LibreOffice)",
                        icon = Icons.Filled.TableChart,
                        onClick = onExportCsv,
                    )
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
                                text = "Copie dans mon compte Google",
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
                            text = if (cloudBackupPossible) {
                                "Désactivée par défaut. Si vous l'activez, c'est Android qui copie votre potager dans votre compte Google, chiffré avec le code de verrouillage de votre téléphone : ni Google ni Sillon ne peuvent le lire. Pour le retrouver sur un autre téléphone, il faudra saisir le code de l'ancien. Désactiver ce réglage n'efface pas une copie déjà envoyée : supprimez-la depuis la sauvegarde de votre compte Google. L'export JSON ci-dessus reste le moyen le plus sûr de changer de téléphone."
                            } else {
                                "Indisponible : définissez d'abord un code, un schéma ou un mot de passe de verrouillage d'écran. Sans lui, Android ne chiffre pas la copie et Sillon ne l'envoie pas."
                            },
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
                                text = "Pilmil Studio · Mentions légales",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Alfortville, France",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Éditeur : Pilmil Studio (Alfortville, France).\n" +
                               "• Aucune permission Internet dans le manifeste Android.\n" +
                               "• Zéro traceur, zéro SDK publicitaire, zéro compte.\n" +
                               "• Calendrier, associations et diagnostic 100 % calculés sur votre téléphone.\n" +
                               "• Logiciel libre sous licence Mozilla Public License 2.0 (MPL-2.0).\n" +
                               "• Contact : pilmil.studio@proton.me · https://relthdrarxash.github.io",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}
