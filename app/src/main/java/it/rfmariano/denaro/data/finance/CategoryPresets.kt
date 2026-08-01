package it.rfmariano.denaro.data.finance

import it.rfmariano.denaro.data.local.TransactionType

enum class StarterCategoryLanguage { ITALIAN, ENGLISH }

internal data class StarterCategory(
    val type: TransactionType,
    val name: String,
    val iconName: String,
    val children: List<Pair<String, String>> = emptyList(),
)

internal fun starterCategories(language: StarterCategoryLanguage): List<StarterCategory> =
    when (language) {
        StarterCategoryLanguage.ITALIAN -> listOf(
            StarterCategory(TransactionType.EXPENSE, "Casa", "house", listOf("Affitto o mutuo" to "house", "Utenze" to "lightbulb", "Manutenzione" to "wrench")),
            StarterCategory(TransactionType.EXPENSE, "Alimentazione", "utensils", listOf("Spesa" to "shopping_basket", "Ristoranti e bar" to "utensils")),
            StarterCategory(TransactionType.EXPENSE, "Trasporti", "car", listOf("Carburante" to "fuel", "Trasporto pubblico" to "bus")),
            StarterCategory(TransactionType.EXPENSE, "Salute", "heart_pulse", listOf("Farmaci" to "pill", "Visite" to "stethoscope")),
            StarterCategory(TransactionType.EXPENSE, "Tempo libero", "gamepad_2", listOf("Intrattenimento" to "clapperboard", "Viaggi" to "plane")),
            StarterCategory(TransactionType.EXPENSE, "Acquisti", "shopping_bag", listOf("Abbigliamento" to "shirt", "Articoli per la casa" to "armchair")),
            StarterCategory(TransactionType.EXPENSE, "Servizi", "receipt_text", listOf("Abbonamenti" to "refresh_cw", "Telefono e internet" to "wifi")),
            StarterCategory(TransactionType.EXPENSE, "Altro", "shapes", listOf("Regali e donazioni" to "gift", "Tasse e commissioni" to "landmark")),
            StarterCategory(TransactionType.INCOME, "Lavoro", "briefcase_business", listOf("Stipendio" to "banknote", "Bonus" to "badge_dollar_sign")),
            StarterCategory(TransactionType.INCOME, "Attività autonoma", "laptop", listOf("Compensi" to "wallet")),
            StarterCategory(TransactionType.INCOME, "Investimenti", "chart_no_axes_combined", listOf("Interessi" to "percent", "Dividendi" to "hand_coins")),
            StarterCategory(TransactionType.INCOME, "Rimborsi", "rotate_ccw"),
            StarterCategory(TransactionType.INCOME, "Altro", "shapes", listOf("Regali" to "gift")),
        )

        StarterCategoryLanguage.ENGLISH -> listOf(
            StarterCategory(TransactionType.EXPENSE, "Home", "house", listOf("Rent or mortgage" to "house", "Utilities" to "lightbulb", "Maintenance" to "wrench")),
            StarterCategory(TransactionType.EXPENSE, "Food", "utensils", listOf("Groceries" to "shopping_basket", "Restaurants and bars" to "utensils")),
            StarterCategory(TransactionType.EXPENSE, "Transport", "car", listOf("Fuel" to "fuel", "Public transport" to "bus")),
            StarterCategory(TransactionType.EXPENSE, "Health", "heart_pulse", listOf("Medicine" to "pill", "Medical visits" to "stethoscope")),
            StarterCategory(TransactionType.EXPENSE, "Leisure", "gamepad_2", listOf("Entertainment" to "clapperboard", "Travel" to "plane")),
            StarterCategory(TransactionType.EXPENSE, "Shopping", "shopping_bag", listOf("Clothing" to "shirt", "Household items" to "armchair")),
            StarterCategory(TransactionType.EXPENSE, "Services", "receipt_text", listOf("Subscriptions" to "refresh_cw", "Phone and internet" to "wifi")),
            StarterCategory(TransactionType.EXPENSE, "Other", "shapes", listOf("Gifts and donations" to "gift", "Taxes and fees" to "landmark")),
            StarterCategory(TransactionType.INCOME, "Work", "briefcase_business", listOf("Salary" to "banknote", "Bonus" to "badge_dollar_sign")),
            StarterCategory(TransactionType.INCOME, "Freelance", "laptop", listOf("Fees" to "wallet")),
            StarterCategory(TransactionType.INCOME, "Investments", "chart_no_axes_combined", listOf("Interest" to "percent", "Dividends" to "hand_coins")),
            StarterCategory(TransactionType.INCOME, "Refunds", "rotate_ccw"),
            StarterCategory(TransactionType.INCOME, "Other", "shapes", listOf("Gifts" to "gift")),
        )
    }
