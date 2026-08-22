package it.rfmariano.denaro.data.backup

import java.io.File
import java.util.UUID

internal object BackupTemporaryFiles {
    private const val RESTORE_PREFIX = "denaro-restore-"
    private const val EXPORT_PREFIX = "denaro-export-"
    private const val SUFFIX = ".tmp"
    private val processToken = UUID.randomUUID().toString()
    private val currentRestorePrefix = "$RESTORE_PREFIX$processToken-"
    private val currentExportPrefix = "$EXPORT_PREFIX$processToken-"

    fun createRestore(cacheDirectory: File): File =
        File.createTempFile(currentRestorePrefix, SUFFIX, cacheDirectory)

    fun createExport(cacheDirectory: File): File =
        File.createTempFile(currentExportPrefix, SUFFIX, cacheDirectory)

    fun deleteStale(cacheDirectory: File) {
        cacheDirectory.listFiles().orEmpty().forEach { file ->
            val isRestore = file.name.startsWith(RESTORE_PREFIX)
            val isExport = file.name.startsWith(EXPORT_PREFIX)
            val belongsToCurrentProcess =
                file.name.startsWith(currentRestorePrefix) ||
                    file.name.startsWith(currentExportPrefix)
            if (file.isFile && file.name.endsWith(SUFFIX) &&
                (isRestore || isExport) && !belongsToCurrentProcess
            ) {
                runCatching { file.delete() }
            }
        }
    }
}
