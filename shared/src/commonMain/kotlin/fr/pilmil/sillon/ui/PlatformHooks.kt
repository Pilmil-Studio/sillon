package fr.pilmil.sillon.ui

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
expect fun rememberReminderPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit

/**
 * Demande l'autorisation d'envoyer des notifications, sans rien activer d'autre (contrairement à
 * [rememberReminderPermissionRequester], qui lance aussi les rappels de semis sur iOS).
 */
@Composable
expect fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit

/**
 * Mesure indicative du capteur de luminosité ambiante (en lux), sans permission.
 * Renvoie `null` si l'appareil n'expose pas de capteur de lumière (ex. iOS).
 */
@Composable
expect fun rememberAmbientLuxSensor(): Float?
