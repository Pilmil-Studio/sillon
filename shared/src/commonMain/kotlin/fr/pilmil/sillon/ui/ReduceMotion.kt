package fr.pilmil.sillon.ui

import androidx.compose.runtime.Composable

/**
 * Vrai quand le système demande de réduire les animations : l'échelle de durée des animations à 0 sur Android
 * (réglage « Supprimer les animations »), « Réduire les animations » sur iOS. Les transitions de la scène du
 * potager sont alors remplacées par un changement instantané. La valeur suit le réglage pendant que l'appli est ouverte.
 */
@Composable
expect fun rememberReduceMotion(): Boolean
