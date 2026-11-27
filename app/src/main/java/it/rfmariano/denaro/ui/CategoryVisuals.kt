package it.rfmariano.denaro.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import it.rfmariano.denaro.R
import java.text.Normalizer

data class CategoryIconOption(
    val name: String,
    @DrawableRes val drawable: Int,
    val aliases: Set<String> = emptySet(),
)

const val CATEGORY_ICON_SUGGESTION_DEBOUNCE_MS = 300L

val CategoryIconOptions = listOf(
    CategoryIconOption("shapes", R.drawable.ms_category, setOf("altro", "other")),
    CategoryIconOption(
        "house",
        R.drawable.ms_home,
        setOf("casa", "home", "affitto", "rent", "mutuo", "mortgage")
    ),
    CategoryIconOption(
        "lightbulb",
        R.drawable.ms_lightbulb,
        setOf("utenze", "utilities", "energia")
    ),
    CategoryIconOption(
        "wrench",
        R.drawable.ms_build,
        setOf("manutenzione", "maintenance", "riparazioni")
    ),
    CategoryIconOption(
        "utensils",
        R.drawable.ms_restaurant,
        setOf("alimentazione", "food", "ristoranti", "restaurants", "bar")
    ),
    CategoryIconOption(
        "shopping_basket",
        R.drawable.ms_shopping_basket,
        setOf("spesa", "groceries", "supermercato")
    ),
    CategoryIconOption(
        "car",
        R.drawable.ms_directions_car,
        setOf("auto", "trasporti", "transport")
    ),
    CategoryIconOption(
        "fuel",
        R.drawable.ms_local_gas_station,
        setOf("carburante", "benzina", "fuel", "gas")
    ),
    CategoryIconOption(
        "bus",
        R.drawable.ms_directions_bus,
        setOf("bus", "trasporto pubblico", "public transport")
    ),
    CategoryIconOption(
        "heart_pulse",
        R.drawable.ms_monitor_heart,
        setOf("salute", "health")
    ),
    CategoryIconOption(
        "pill",
        R.drawable.ms_pill,
        setOf("farmaci", "medicine", "pharmacy")
    ),
    CategoryIconOption(
        "stethoscope",
        R.drawable.ms_stethoscope,
        setOf("visite", "medical", "doctor")
    ),
    CategoryIconOption(
        "gamepad_2",
        R.drawable.ms_sports_esports,
        setOf("tempo libero", "leisure", "giochi", "games")
    ),
    CategoryIconOption(
        "clapperboard",
        R.drawable.ms_movie,
        setOf("intrattenimento", "entertainment", "cinema")
    ),
    CategoryIconOption(
        "plane",
        R.drawable.ms_flight,
        setOf("viaggi", "travel", "vacanze")
    ),
    CategoryIconOption(
        "shopping_bag",
        R.drawable.ms_shopping_bag,
        setOf("acquisti", "shopping")
    ),
    CategoryIconOption(
        "shirt",
        R.drawable.ms_apparel,
        setOf("abbigliamento", "clothing")
    ),
    CategoryIconOption(
        "armchair",
        R.drawable.ms_chair,
        setOf("arredamento", "household", "furniture")
    ),
    CategoryIconOption(
        "receipt_text",
        R.drawable.ms_request_quote,
        setOf("servizi", "services", "bollette")
    ),
    CategoryIconOption(
        "refresh_cw",
        R.drawable.ms_autorenew,
        setOf("abbonamenti", "subscriptions", "ricorrenti")
    ),
    CategoryIconOption(
        "wifi",
        R.drawable.ms_wifi,
        setOf("internet", "telefono", "phone", "wifi")
    ),
    CategoryIconOption(
        "gift",
        R.drawable.ms_redeem,
        setOf("regali", "gifts", "donazioni", "donations")
    ),
    CategoryIconOption(
        "landmark",
        R.drawable.ms_account_balance,
        setOf("tasse", "taxes", "commissioni", "fees", "banca")
    ),
    CategoryIconOption(
        "briefcase_business",
        R.drawable.ms_work,
        setOf("lavoro", "work", "job")
    ),
    CategoryIconOption(
        "banknote",
        R.drawable.ms_payments,
        setOf("stipendio", "salary", "paga")
    ),
    CategoryIconOption(
        "badge_dollar_sign",
        R.drawable.ms_paid,
        setOf("bonus", "premio")
    ),
    CategoryIconOption(
        "laptop",
        R.drawable.ms_laptop,
        setOf("freelance", "attivita autonoma", "compensi")
    ),
    CategoryIconOption(
        "wallet",
        R.drawable.ms_account_balance_wallet,
        setOf("compensi", "fees", "portafoglio")
    ),
    CategoryIconOption(
        "chart_no_axes_combined",
        R.drawable.ms_trending_up,
        setOf("investimenti", "investments")
    ),
    CategoryIconOption(
        "percent",
        R.drawable.ms_percent,
        setOf("interessi", "interest")
    ),
    CategoryIconOption(
        "hand_coins",
        R.drawable.ms_handshake,
        setOf("dividendi", "dividends")
    ),
    CategoryIconOption(
        "rotate_ccw",
        R.drawable.ms_currency_exchange,
        setOf("rimborsi", "refunds")
    ),
    CategoryIconOption(
        "circle_help",
        R.drawable.ms_help,
        setOf("senza categoria", "uncategorized")
    ),
    CategoryIconOption(
        "coffee",
        R.drawable.ms_local_cafe,
        setOf("caffe", "colazione", "coffee", "breakfast")
    ),
    CategoryIconOption(
        "cake_slice",
        R.drawable.ms_cake,
        setOf("dolci", "torta", "compleanno", "cake", "birthday")
    ),
    CategoryIconOption(
        "dumbbell",
        R.drawable.ms_fitness_center,
        setOf("sport", "palestra", "fitness", "gym")
    ),
    CategoryIconOption(
        "baby",
        R.drawable.ms_child_care,
        setOf("bambini", "figli", "neonato", "children", "kids")
    ),
    CategoryIconOption(
        "paw_print",
        R.drawable.ms_pets,
        setOf("animali", "cane", "gatto", "pets", "dog", "cat")
    ),
    CategoryIconOption(
        "book_open",
        R.drawable.ms_menu_book,
        setOf("libri", "studio", "lettura", "books", "study")
    ),
    CategoryIconOption(
        "graduation_cap",
        R.drawable.ms_school,
        setOf("istruzione", "universita", "scuola", "education", "school")
    ),
    CategoryIconOption(
        "music",
        R.drawable.ms_music_note,
        setOf("musica", "concerti", "music", "concerts")
    ),
    CategoryIconOption(
        "headphones",
        R.drawable.ms_headphones,
        setOf("audio", "podcast", "cuffie")
    ),
    CategoryIconOption(
        "camera",
        R.drawable.ms_photo_camera,
        setOf("fotografia", "foto", "photography")
    ),
    CategoryIconOption(
        "smartphone",
        R.drawable.ms_smartphone,
        setOf("telefono", "cellulare", "phone", "mobile")
    ),
    CategoryIconOption(
        "tv",
        R.drawable.ms_tv,
        setOf("televisione", "streaming", "serie", "television")
    ),
    CategoryIconOption(
        "bed_double",
        R.drawable.ms_bed,
        setOf("hotel", "alloggio", "camera", "accommodation")
    ),
    CategoryIconOption(
        "hammer",
        R.drawable.ms_hardware,
        setOf("lavori", "fai da te", "attrezzi", "tools", "diy")
    ),
    CategoryIconOption(
        "paintbrush",
        R.drawable.ms_brush,
        setOf("pittura", "decorazione", "arte", "paint", "art")
    ),
    CategoryIconOption(
        "trees",
        R.drawable.ms_park,
        setOf("natura", "giardino", "parco", "nature", "garden")
    ),
    CategoryIconOption(
        "bike",
        R.drawable.ms_pedal_bike,
        setOf("bici", "bicicletta", "cycling", "bicycle")
    ),
    CategoryIconOption(
        "train_front",
        R.drawable.ms_train,
        setOf("treno", "ferrovia", "train", "rail")
    ),
    CategoryIconOption(
        "ship",
        R.drawable.ms_directions_boat,
        setOf("nave", "traghetto", "crociera", "ferry", "cruise")
    ),
    CategoryIconOption(
        "ticket",
        R.drawable.ms_confirmation_number,
        setOf("biglietti", "eventi", "tickets", "events")
    ),
    CategoryIconOption(
        "calendar_days",
        R.drawable.ms_calendar_month,
        setOf("calendario", "appuntamenti", "calendar", "appointments")
    ),
    CategoryIconOption(
        "shield",
        R.drawable.ms_shield,
        setOf("assicurazione", "protezione", "insurance", "protection")
    ),
    CategoryIconOption(
        "piggy_bank",
        R.drawable.ms_savings,
        setOf("risparmio", "salvadanaio", "savings")
    ),
    CategoryIconOption(
        "coins",
        R.drawable.ms_toll,
        setOf("monete", "contanti", "cash", "money")
    ),
    CategoryIconOption(
        "credit_card",
        R.drawable.ms_credit_card,
        setOf("carta", "pagamenti", "credito", "card", "payments")
    ),
    CategoryIconOption(
        "building_2",
        R.drawable.ms_apartment,
        setOf("ufficio", "azienda", "impresa", "office", "company")
    ),
    CategoryIconOption(
        "store",
        R.drawable.ms_storefront,
        setOf("negozio", "commercio", "shop", "retail")
    ),
)

fun suggestCategoryIcon(value: String): String {
    val query = value.normalizedSearchText()
    if (query.isBlank()) return "shapes"
    val match = CategoryIconOptions.maxByOrNull { it.matchScore(query) }
    return match?.takeIf { it.matchScore(query) > 0 }?.name ?: "shapes"
}

fun searchCategoryIcons(value: String): List<CategoryIconOption> {
    val query = value.normalizedSearchText()
    if (query.isBlank()) return CategoryIconOptions
    return CategoryIconOptions
        .map { it to it.matchScore(query) }
        .filter { (_, score) -> score > 0 }
        .sortedWith(compareByDescending<Pair<CategoryIconOption, Int>> { it.second }.thenBy { it.first.name })
        .map(Pair<CategoryIconOption, Int>::first)
}

fun categoryIconOption(name: String?): CategoryIconOption =
    CategoryIconOptions.firstOrNull { it.name == name } ?: CategoryIconOptions.first()

private fun String.normalizedSearchText(): String = Normalizer.normalize(
    lowercase().trim(),
    Normalizer.Form.NFD,
).replace(Regex("\\p{Mn}+"), "").replace('_', ' ')

private fun CategoryIconOption.matchScore(query: String): Int =
    (aliases + name.replace('_', ' ')).maxOf { candidate ->
        fuzzyMatchScore(query, candidate.normalizedSearchText())
    }

private fun fuzzyMatchScore(query: String, candidate: String): Int = when {
    candidate == query -> 1_000
    candidate.startsWith(query) -> 800 - (candidate.length - query.length)
    candidate.contains(query) -> 600 - (candidate.length - query.length)
    else -> {
        var candidateIndex = 0
        var firstMatch = -1
        var lastMatch = -1
        query.forEach { character ->
            val match = candidate.indexOf(character, candidateIndex)
            if (match < 0) return 0
            if (firstMatch < 0) firstMatch = match
            lastMatch = match
            candidateIndex = match + 1
        }
        400 - (lastMatch - firstMatch - query.length + 1) - firstMatch
    }
}

val CategoryPalette = listOf(
    Color(0xFFD32F2F), Color(0xFFC2185B), Color(0xFF7B1FA2), Color(0xFF512DA8),
    Color(0xFF303F9F), Color(0xFF1976D2), Color(0xFF00796B), Color(0xFF388E3C),
    Color(0xFF689F38), Color(0xFFF57C00), Color(0xFFE64A19), Color(0xFF5D4037),
)

@Composable
fun CategoryIcon(
    iconName: String?,
    colorIndex: Int?,
    modifier: Modifier = Modifier,
) {
    val color = CategoryPalette[(colorIndex ?: 0).mod(CategoryPalette.size)]
    Box(
        modifier = modifier
            .size(42.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(categoryIconOption(iconName).drawable),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(21.dp),
        )
    }
}
