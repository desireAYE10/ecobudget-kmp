package com.example.shared.model

import com.example.shared.resources.Res
import com.example.shared.resources.category_alimentation
import com.example.shared.resources.category_logement
import com.example.shared.resources.category_loisirs
import com.example.shared.resources.category_transport
import org.jetbrains.compose.resources.StringResource

/**
 * Représente les catégories obligatoires pour la classification des dépenses dans EcoBudget.
 *
 * Le libellé n'est plus une référence à `com.example.R` (dépendance Android exclusive) mais
 * une [StringResource] issue du catalogue de ressources partagé Compose Multiplatform
 * (`commonMain/composeResources/values/strings.xml`), résolvable aussi bien depuis l'app
 * Android que depuis une cible iOS.
 */
enum class Category(
    val labelRes: StringResource,
    val emoji: String
) {
    TRANSPORT(Res.string.category_transport, "🚌"),
    ALIMENTATION(Res.string.category_alimentation, "🍱"),
    LOISIRS(Res.string.category_loisirs, "🎾"),
    LOGEMENT(Res.string.category_logement, "🏠")
}
