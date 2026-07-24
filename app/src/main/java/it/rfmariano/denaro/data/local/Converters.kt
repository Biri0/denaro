package it.rfmariano.denaro.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun transactionTypeToString(value: TransactionType): String = value.name

    @TypeConverter
    fun stringToTransactionType(value: String): TransactionType =
        TransactionType.valueOf(value)

    @TypeConverter
    fun recurrenceFrequencyToString(value: RecurrenceFrequency): String = value.name

    @TypeConverter
    fun stringToRecurrenceFrequency(value: String): RecurrenceFrequency =
        RecurrenceFrequency.valueOf(value)

    @TypeConverter
    fun legacyImportStatusToString(value: LegacyImportStatus): String = value.name

    @TypeConverter
    fun stringToLegacyImportStatus(value: String): LegacyImportStatus =
        LegacyImportStatus.valueOf(value)
}
