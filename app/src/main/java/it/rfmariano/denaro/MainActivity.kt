package it.rfmariano.denaro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.rfmariano.denaro.data.migration.LegacyMigrationCoordinator
import it.rfmariano.denaro.data.migration.MigrationResult
import it.rfmariano.denaro.ui.DenaroApp
import it.rfmariano.denaro.ui.theme.DenaroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DenaroTheme {
                MigrationGate {
                    DenaroApp(
                        repository = (application as DenaroApplication).financeRepository,
                    )
                }
            }
        }
    }
}

@Composable
private fun MigrationGate(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val coordinator = remember(context) {
        LegacyMigrationCoordinator(context)
    }
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<MigrationResult?>(null) }

    LaunchedEffect(attempt) {
        result = null
        result = coordinator.migrateIfNeeded()
    }

    when (val current = result) {
        null -> MigrationProgress()
        MigrationResult.NotNeeded,
        is MigrationResult.Success,
            -> content()

        is MigrationResult.Failure -> MigrationFailure(
            message = current.message,
            onRetry = { attempt++ },
        )
    }
}

@Composable
private fun MigrationProgress() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(40.dp))
        Text(
            text = stringResource(R.string.migration_progress),
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun MigrationFailure(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(R.string.migration_failed))
        Text(
            text = message,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.retry))
        }
    }
}
