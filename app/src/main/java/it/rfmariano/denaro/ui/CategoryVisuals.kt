package it.rfmariano.denaro.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import com.composables.icons.lucide.R as LucideR

data class CategoryIconOption(
    val name: String,
    @DrawableRes val drawable: Int,
    val aliases: Set<String> = emptySet(),
)

const val CATEGORY_ICON_SUGGESTION_DEBOUNCE_MS = 300L

val CategoryIconOptions = listOf(
    CategoryIconOption("shapes", LucideR.drawable.lucide_ic_shapes, setOf("altro", "other")),
    CategoryIconOption(
        "house",
        LucideR.drawable.lucide_ic_house,
        setOf("casa", "home", "affitto", "rent", "mutuo", "mortgage")
    ),
    CategoryIconOption(
        "lightbulb",
        LucideR.drawable.lucide_ic_lightbulb,
        setOf("utenze", "utilities", "energia")
    ),
    CategoryIconOption(
        "wrench",
        LucideR.drawable.lucide_ic_wrench,
        setOf("manutenzione", "maintenance", "riparazioni")
    ),
    CategoryIconOption(
        "utensils",
        LucideR.drawable.lucide_ic_utensils,
        setOf("alimentazione", "food", "ristoranti", "restaurants", "bar")
    ),
    CategoryIconOption(
        "shopping_basket",
        LucideR.drawable.lucide_ic_shopping_basket,
        setOf("spesa", "groceries", "supermercato")
    ),
    CategoryIconOption(
        "car",
        LucideR.drawable.lucide_ic_car,
        setOf("auto", "trasporti", "transport")
    ),
    CategoryIconOption(
        "fuel",
        LucideR.drawable.lucide_ic_fuel,
        setOf("carburante", "benzina", "fuel", "gas")
    ),
    CategoryIconOption(
        "bus",
        LucideR.drawable.lucide_ic_bus,
        setOf("bus", "trasporto pubblico", "public transport")
    ),
    CategoryIconOption(
        "heart_pulse",
        LucideR.drawable.lucide_ic_heart_pulse,
        setOf("salute", "health")
    ),
    CategoryIconOption(
        "pill",
        LucideR.drawable.lucide_ic_pill,
        setOf("farmaci", "medicine", "pharmacy")
    ),
    CategoryIconOption(
        "stethoscope",
        LucideR.drawable.lucide_ic_stethoscope,
        setOf("visite", "medical", "doctor")
    ),
    CategoryIconOption(
        "gamepad_2",
        LucideR.drawable.lucide_ic_gamepad_2,
        setOf("tempo libero", "leisure", "giochi", "games")
    ),
    CategoryIconOption(
        "clapperboard",
        LucideR.drawable.lucide_ic_clapperboard,
        setOf("intrattenimento", "entertainment", "cinema")
    ),
    CategoryIconOption(
        "plane",
        LucideR.drawable.lucide_ic_plane,
        setOf("viaggi", "travel", "vacanze")
    ),
    CategoryIconOption(
        "shopping_bag",
        LucideR.drawable.lucide_ic_shopping_bag,
        setOf("acquisti", "shopping")
    ),
    CategoryIconOption(
        "shirt",
        LucideR.drawable.lucide_ic_shirt,
        setOf("abbigliamento", "clothing")
    ),
    CategoryIconOption(
        "armchair",
        LucideR.drawable.lucide_ic_armchair,
        setOf("arredamento", "household", "furniture")
    ),
    CategoryIconOption(
        "receipt_text",
        LucideR.drawable.lucide_ic_receipt_text,
        setOf("servizi", "services", "bollette")
    ),
    CategoryIconOption(
        "refresh_cw",
        LucideR.drawable.lucide_ic_refresh_cw,
        setOf("abbonamenti", "subscriptions", "ricorrenti")
    ),
    CategoryIconOption(
        "wifi",
        LucideR.drawable.lucide_ic_wifi,
        setOf("internet", "telefono", "phone", "wifi")
    ),
    CategoryIconOption(
        "gift",
        LucideR.drawable.lucide_ic_gift,
        setOf("regali", "gifts", "donazioni", "donations")
    ),
    CategoryIconOption(
        "landmark",
        LucideR.drawable.lucide_ic_landmark,
        setOf("tasse", "taxes", "commissioni", "fees", "banca")
    ),
    CategoryIconOption(
        "briefcase_business",
        LucideR.drawable.lucide_ic_briefcase_business,
        setOf("lavoro", "work", "job")
    ),
    CategoryIconOption(
        "banknote",
        LucideR.drawable.lucide_ic_banknote,
        setOf("stipendio", "salary", "paga")
    ),
    CategoryIconOption(
        "badge_dollar_sign",
        LucideR.drawable.lucide_ic_badge_dollar_sign,
        setOf("bonus", "premio")
    ),
    CategoryIconOption(
        "laptop",
        LucideR.drawable.lucide_ic_laptop,
        setOf("freelance", "attivita autonoma", "compensi")
    ),
    CategoryIconOption(
        "wallet",
        LucideR.drawable.lucide_ic_wallet,
        setOf("compensi", "fees", "portafoglio")
    ),
    CategoryIconOption(
        "chart_no_axes_combined",
        LucideR.drawable.lucide_ic_chart_no_axes_combined,
        setOf("investimenti", "investments")
    ),
    CategoryIconOption(
        "percent",
        LucideR.drawable.lucide_ic_percent,
        setOf("interessi", "interest")
    ),
    CategoryIconOption(
        "hand_coins",
        LucideR.drawable.lucide_ic_hand_coins,
        setOf("dividendi", "dividends")
    ),
    CategoryIconOption(
        "rotate_ccw",
        LucideR.drawable.lucide_ic_rotate_ccw,
        setOf("rimborsi", "refunds")
    ),
    CategoryIconOption(
        "circle_help",
        LucideR.drawable.lucide_ic_circle_question_mark,
        setOf("senza categoria", "uncategorized")
    ),
    CategoryIconOption(
        "coffee",
        LucideR.drawable.lucide_ic_coffee,
        setOf("caffe", "colazione", "coffee", "breakfast")
    ),
    CategoryIconOption(
        "cake_slice",
        LucideR.drawable.lucide_ic_cake_slice,
        setOf("dolci", "torta", "compleanno", "cake", "birthday")
    ),
    CategoryIconOption(
        "dumbbell",
        LucideR.drawable.lucide_ic_dumbbell,
        setOf("sport", "palestra", "fitness", "gym")
    ),
    CategoryIconOption(
        "baby",
        LucideR.drawable.lucide_ic_baby,
        setOf("bambini", "figli", "neonato", "children", "kids")
    ),
    CategoryIconOption(
        "paw_print",
        LucideR.drawable.lucide_ic_paw_print,
        setOf("animali", "cane", "gatto", "pets", "dog", "cat")
    ),
    CategoryIconOption(
        "book_open",
        LucideR.drawable.lucide_ic_book_open,
        setOf("libri", "studio", "lettura", "books", "study")
    ),
    CategoryIconOption(
        "graduation_cap",
        LucideR.drawable.lucide_ic_graduation_cap,
        setOf("istruzione", "universita", "scuola", "education", "school")
    ),
    CategoryIconOption(
        "music",
        LucideR.drawable.lucide_ic_music,
        setOf("musica", "concerti", "music", "concerts")
    ),
    CategoryIconOption(
        "headphones",
        LucideR.drawable.lucide_ic_headphones,
        setOf("audio", "podcast", "cuffie")
    ),
    CategoryIconOption(
        "camera",
        LucideR.drawable.lucide_ic_camera,
        setOf("fotografia", "foto", "photography")
    ),
    CategoryIconOption(
        "smartphone",
        LucideR.drawable.lucide_ic_smartphone,
        setOf("telefono", "cellulare", "phone", "mobile")
    ),
    CategoryIconOption(
        "tv",
        LucideR.drawable.lucide_ic_tv,
        setOf("televisione", "streaming", "serie", "television")
    ),
    CategoryIconOption(
        "bed_double",
        LucideR.drawable.lucide_ic_bed_double,
        setOf("hotel", "alloggio", "camera", "accommodation")
    ),
    CategoryIconOption(
        "hammer",
        LucideR.drawable.lucide_ic_hammer,
        setOf("lavori", "fai da te", "attrezzi", "tools", "diy")
    ),
    CategoryIconOption(
        "paintbrush",
        LucideR.drawable.lucide_ic_paintbrush,
        setOf("pittura", "decorazione", "arte", "paint", "art")
    ),
    CategoryIconOption(
        "trees",
        LucideR.drawable.lucide_ic_trees,
        setOf("natura", "giardino", "parco", "nature", "garden")
    ),
    CategoryIconOption(
        "bike",
        LucideR.drawable.lucide_ic_bike,
        setOf("bici", "bicicletta", "cycling", "bicycle")
    ),
    CategoryIconOption(
        "train_front",
        LucideR.drawable.lucide_ic_train_front,
        setOf("treno", "ferrovia", "train", "rail")
    ),
    CategoryIconOption(
        "ship",
        LucideR.drawable.lucide_ic_ship,
        setOf("nave", "traghetto", "crociera", "ferry", "cruise")
    ),
    CategoryIconOption(
        "ticket",
        LucideR.drawable.lucide_ic_ticket,
        setOf("biglietti", "eventi", "tickets", "events")
    ),
    CategoryIconOption(
        "calendar_days",
        LucideR.drawable.lucide_ic_calendar_days,
        setOf("calendario", "appuntamenti", "calendar", "appointments")
    ),
    CategoryIconOption(
        "shield",
        LucideR.drawable.lucide_ic_shield,
        setOf("assicurazione", "protezione", "insurance", "protection")
    ),
    CategoryIconOption(
        "piggy_bank",
        LucideR.drawable.lucide_ic_piggy_bank,
        setOf("risparmio", "salvadanaio", "savings")
    ),
    CategoryIconOption(
        "coins",
        LucideR.drawable.lucide_ic_coins,
        setOf("monete", "contanti", "cash", "money")
    ),
    CategoryIconOption(
        "credit_card",
        LucideR.drawable.lucide_ic_credit_card,
        setOf("carta", "pagamenti", "credito", "card", "payments")
    ),
    CategoryIconOption(
        "building_2",
        LucideR.drawable.lucide_ic_building_2,
        setOf("ufficio", "azienda", "impresa", "office", "company")
    ),
    CategoryIconOption(
        "store",
        LucideR.drawable.lucide_ic_store,
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
            .size(40.dp)
            .background(color.copy(alpha = 0.14f), CircleShape),
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
