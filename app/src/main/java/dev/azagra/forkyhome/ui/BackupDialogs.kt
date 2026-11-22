package dev.azagra.forkyhome.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.azagra.forkyhome.R
import dev.azagra.forkyhome.data.AppInfo
import dev.azagra.forkyhome.data.BackupManager
import dev.azagra.forkyhome.data.IconPacks
import dev.azagra.forkyhome.data.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Stable
class BackupController(
    val startExport: () -> Unit,
    val startImport: () -> Unit,
)

@Composable
fun rememberBackupController(
    settings: LauncherSettings,
    allApps: List<AppInfo>,
    packsProvider: suspend () -> List<IconPacks.Pack>,
    onAuthenticate: ((() -> Unit) -> Unit)?,
    onApplyRestored: suspend (LauncherSettings) -> Unit,
): BackupController {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentSettings by rememberUpdatedState(settings)
    val currentApps by rememberUpdatedState(allApps)
    val currentOnAuthenticate by rememberUpdatedState(onAuthenticate)

    var showExportDialog by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingEncryptedJson by remember { mutableStateOf<String?>(null) }
    var restoreSummary by remember { mutableStateOf<BackupManager.RestorePlan?>(null) }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = BackupManager.exportToJson(currentSettings, password)
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open output stream")
                }.isSuccess
            }
            Toast.makeText(
                ctx,
                ctx.getString(if (ok) R.string.backup_export_success else R.string.backup_export_error),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    suspend fun applyPlan(restored: LauncherSettings) {
        val installedKeys = currentApps.map { it.key }.toSet()
        val installedPacks = withContext(Dispatchers.IO) {
            runCatching { packsProvider().map { it.pkg }.toSet() }.getOrDefault(emptySet())
        }
        val plan = BackupManager.prepareForRestore(restored, installedKeys, installedPacks)
        onApplyRestored(plan.settings)
        restoreSummary = plan
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readNBytes(BackupManager.MAX_BACKUP_BYTES + 1)
                        if (bytes.size > BackupManager.MAX_BACKUP_BYTES) null
                        else String(bytes, Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (raw == null) {
                Toast.makeText(ctx, ctx.getString(R.string.backup_invalid_file), Toast.LENGTH_SHORT).show()
                return@launch
            }
            when (val inspected = withContext(Dispatchers.Default) { BackupManager.inspect(raw) }) {
                is BackupManager.InspectResult.Plain -> applyPlan(inspected.settings)
                is BackupManager.InspectResult.NeedsPassword -> pendingEncryptedJson = inspected.rawJson
                BackupManager.InspectResult.Invalid -> {
                    Toast.makeText(ctx, ctx.getString(R.string.backup_invalid_file), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showExportDialog) {
        ExportBackupDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { password ->
                showExportDialog = false
                pendingExportPassword = password
                val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                createDocLauncher.launch("forkyhome-backup-$date.json")
            },
        )
    }

    val encryptedRaw = pendingEncryptedJson
    if (encryptedRaw != null) {
        ImportPasswordDialog(
            onDismiss = { pendingEncryptedJson = null },
            onDecrypt = { password ->
                when (val res = withContext(Dispatchers.Default) { BackupManager.decrypt(encryptedRaw, password) }) {
                    is BackupManager.DecryptResult.Success -> {
                        pendingEncryptedJson = null
                        applyPlan(res.settings)
                        true
                    }
                    BackupManager.DecryptResult.WrongPassword -> false
                    BackupManager.DecryptResult.Invalid -> {
                        pendingEncryptedJson = null
                        Toast.makeText(ctx, ctx.getString(R.string.backup_invalid_file), Toast.LENGTH_SHORT).show()
                        true
                    }
                }
            },
        )
    }

    val summary = restoreSummary
    if (summary != null) {
        RestoreSummaryDialog(
            plan = summary,
            onDismiss = { restoreSummary = null },
        )
    }

    return remember {
        BackupController(
            startExport = {
                val auth = currentOnAuthenticate
                if (auth != null) {
                    auth { showExportDialog = true }
                } else {
                    showExportDialog = true
                }
            },
            startImport = {
                openDocLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
        )
    }
}

@Composable
private fun ExportBackupDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String?) -> Unit,
) {
    var encrypt by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.backup_encrypt_toggle),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.backup_encrypt_toggle_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = encrypt,
                        onCheckedChange = {
                            encrypt = it
                            errorRes = null
                        },
                    )
                }

                if (encrypt) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorRes = null
                        },
                        label = { Text(stringResource(R.string.backup_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            errorRes = null
                        },
                        label = { Text(stringResource(R.string.backup_password_confirm_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = errorRes != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    errorRes?.let { resId ->
                        Text(
                            text = stringResource(resId),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!encrypt) {
                        onConfirm(null)
                    } else if (password.isEmpty()) {
                        errorRes = R.string.backup_password_empty
                    } else if (password != confirmPassword) {
                        errorRes = R.string.backup_password_mismatch
                    } else {
                        onConfirm(password)
                    }
                },
            ) {
                Text(stringResource(R.string.backup_export_continue))
            }
        },
    )
}

@Composable
private fun ImportPasswordDialog(
    onDismiss: () -> Unit,
    onDecrypt: suspend (String) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var wrongPassword by remember { mutableStateOf(false) }
    var isDecrypting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isDecrypting) onDismiss() },
        title = { Text(stringResource(R.string.backup_import_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.backup_import_password_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        wrongPassword = false
                    },
                    enabled = !isDecrypting,
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = wrongPassword,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (wrongPassword) {
                    Text(
                        text = stringResource(R.string.backup_wrong_password),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDecrypting) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            Button(
                enabled = !isDecrypting,
                onClick = {
                    if (isDecrypting) return@Button
                    isDecrypting = true
                    scope.launch {
                        val ok = onDecrypt(password)
                        isDecrypting = false
                        if (!ok) wrongPassword = true
                    }
                },
            ) {
                Text(stringResource(R.string.backup_import_action))
            }
        },
    )
}

@Composable
private fun RestoreSummaryDialog(
    plan: BackupManager.RestorePlan,
    onDismiss: () -> Unit,
) {
    val needsPermissionsHint = plan.settings.badgeNumbers ||
        plan.settings.doubleTapToSleep ||
        plan.settings.smartspaceShowCalendar

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_done_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.backup_restore_done_ok))
                if (plan.skippedWidgets > 0) {
                    Text(
                        text = stringResource(R.string.backup_restore_skipped_widgets, plan.skippedWidgets),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (plan.missingApps > 0) {
                    Text(
                        text = stringResource(R.string.backup_restore_missing_apps, plan.missingApps),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (plan.missingIconPack) {
                    Text(
                        text = stringResource(R.string.backup_restore_missing_pack),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (needsPermissionsHint) {
                    Text(
                        text = stringResource(R.string.backup_restore_permissions_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_got_it))
            }
        },
    )
}
