package it.rfmariano.denaro.data.migration

data class LegacyBucket(
    val id: String,
    val title: String,
    val description: String?,
    val initialBalanceMinor: Long,
    val currency: String,
    val createdAt: Long,
)

data class LegacyTransaction(
    val id: String,
    val bucketId: String,
    val amountMinor: Long,
    val description: String?,
    val date: Long,
    val intervalValue: Int?,
    val intervalUnit: String?,
    val dayOfMonth: Int?,
    val month: Int?,
)

data class LegacySnapshot(
    val buckets: List<LegacyBucket>,
    val transactions: List<LegacyTransaction>,
)
