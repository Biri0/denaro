package it.rfmariano.denaro.quickentry

import android.content.Context
import android.content.Intent
import it.rfmariano.denaro.MainActivity

enum class QuickEntryAction {
    INCOME,
    EXPENSE,
    TRANSFER,
    BORROW,
    LEND,
}

data class QuickEntryRequest(
    val id: Long,
    val action: QuickEntryAction,
)

object QuickEntryIntent {
    private const val ACTION_PREFIX = "it.rfmariano.denaro.action.QUICK_ENTRY."

    fun create(context: Context, action: QuickEntryAction): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_PREFIX + action.name)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun parse(intent: Intent?): QuickEntryAction? {
        return parseAction(intent?.action)
    }

    internal fun parseAction(action: String?): QuickEntryAction? {
        val actionName = action?.takeIf { it.startsWith(ACTION_PREFIX) }
            ?.removePrefix(ACTION_PREFIX)
            ?: return null
        return runCatching { QuickEntryAction.valueOf(actionName) }.getOrNull()
    }

    fun clear(intent: Intent): Intent = Intent(intent).setAction(null)
}
