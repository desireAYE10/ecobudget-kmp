package com.example.shared.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Modèle immuable représentant un mois spécifique pour la navigation budgétaire.
 *
 * Migration : la version Android utilisait `java.util.Calendar` et `java.text.SimpleDateFormat`,
 * indisponibles en `commonMain` (ils n'existent pas sur la machine virtuelle Kotlin/Native
 * utilisée pour cibler iOS). Le calcul de date repose désormais sur `kotlinx-datetime`
 * (multiplateforme), et le libellé français est produit à partir d'une table de noms de mois
 * définie en Kotlin pur, car `kotlinx-datetime` ne fournit volontairement aucune API de
 * localisation (dépendante de la plateforme).
 *
 * @property year Année (ex: 2026).
 * @property month Index du mois de 0 (Janvier) à 11 (Décembre), convention conservée à
 * l'identique de la version Android pour ne pas impacter le ViewModel ni l'UI.
 */
data class YearMonth(
    val year: Int,
    val month: Int
) {
    /**
     * Libellé formaté en français (ex: "Août 2026").
     */
    val displayLabel: String
        get() = "${FRENCH_MONTHS[month]} $year"

    /**
     * Retourne le YearMonth précédent.
     */
    fun previous(): YearMonth {
        return if (month == 0) {
            YearMonth(year - 1, 11)
        } else {
            YearMonth(year, month - 1)
        }
    }

    /**
     * Retourne le YearMonth suivant.
     */
    fun next(): YearMonth {
        return if (month == 11) {
            YearMonth(year + 1, 0)
        } else {
            YearMonth(year, month + 1)
        }
    }

    /**
     * Vérifie si un timestamp millisecondes appartient à ce mois précis.
     */
    fun containsTimestamp(timestamp: Long): Boolean {
        val date = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return date.year == year && (date.monthNumber - 1) == month
    }

    companion object {
        private val FRENCH_MONTHS = listOf(
            "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
        )

        /**
         * Crée le YearMonth courant.
         */
        fun current(): YearMonth {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            return YearMonth(year = today.year, month = today.monthNumber - 1)
        }

        /**
         * Crée le YearMonth correspondant à un timestamp.
         */
        fun fromTimestamp(timestamp: Long): YearMonth {
            val date = Instant.fromEpochMilliseconds(timestamp)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
            return YearMonth(year = date.year, month = date.monthNumber - 1)
        }
    }
}
