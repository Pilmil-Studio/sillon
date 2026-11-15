package fr.pilmil.sillon.model

import kotlinx.serialization.Serializable

@Serializable
enum class Climate(val frenchLabel: String) {
    TEMPERATE("Tempéré"),
    TROPICAL("Tropical"),
}
