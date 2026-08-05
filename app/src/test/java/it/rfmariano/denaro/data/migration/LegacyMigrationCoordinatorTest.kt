package it.rfmariano.denaro.data.migration

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyMigrationCoordinatorTest {
    @Test
    fun missingLegacyDatabaseOnlyRunsCleanup() {
        assertEquals(
            LegacyMigrationAction.CLEANUP_ONLY,
            legacyMigrationAction(false),
        )
    }

    @Test
    fun existingLegacyDatabaseRunsNativeDatabaseAudit() {
        assertEquals(
            LegacyMigrationAction.AUDIT_AND_MIGRATE,
            legacyMigrationAction(true),
        )
    }
}
