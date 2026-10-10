package dev.housecalendar.app.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.housecalendar.app.AppGraph
import dev.housecalendar.app.R
import dev.housecalendar.app.model.BackupModel
import dev.housecalendar.app.model.ImportVerdict
import dev.housecalendar.app.ui.components.Field
import dev.housecalendar.app.ui.components.QuietButton
import dev.housecalendar.app.ui.icons.HcIcons
import dev.housecalendar.app.ui.theme.Hc
import dev.housecalendar.app.ui.theme.Space
import dev.housecalendar.contracts.FamilyId
import dev.housecalendar.data.ExportCodec
import dev.housecalendar.data.ExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/** What the last export or import did, as words for the page. */
private sealed interface BackupStatus {
    data object Working : BackupStatus

    data object Exported : BackupStatus

    data class Imported(val added: Int) : BackupStatus

    data class Problem(val message: Int) : BackupStatus
}

/** SPEC FR-48: export the whole log to a passphrase-locked file, or bring one in, which merges like a sync. */
@Composable
fun BackupPage(graph: AppGraph, family: FamilyId, today: LocalDate, allowExport: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<BackupStatus?>(null) }
    val ready = passphrase.length >= MIN_PASSPHRASE
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME)) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        status = BackupStatus.Working
        scope.launch {
            status = try {
                withContext(Dispatchers.IO) {
                    val bytes = ExportCodec.encrypt(graph.opLog.all(), passphrase.toCharArray())
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("no output")
                }
                BackupStatus.Exported
            } catch (e: IOException) {
                BackupStatus.Problem(R.string.backup_corrupt)
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        status = BackupStatus.Working
        scope.launch { status = withContext(Dispatchers.IO) { importFrom(graph, uri, family, passphrase, context) } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Text(stringResource(R.string.backup_body), style = Hc.type.body, color = Hc.colors.ink)
        Field(passphrase, { passphrase = it }, stringResource(R.string.backup_passphrase_hint), label = stringResource(R.string.backup_passphrase), secret = true)
        if (allowExport) QuietButton(stringResource(R.string.backup_export), { export.launch(BackupModel.fileName(today)) }, enabled = ready, icon = HcIcons.Share)
        QuietButton(stringResource(R.string.backup_import), { import.launch(arrayOf("*/*")) }, enabled = ready, icon = HcIcons.Import)
        when (val current = status) {
            null -> Unit
            BackupStatus.Working -> Text(stringResource(R.string.backup_working), style = Hc.type.caption, color = Hc.colors.inkMuted)
            BackupStatus.Exported -> Text(stringResource(R.string.backup_exported), style = Hc.type.body, color = Hc.colors.ink)
            is BackupStatus.Imported -> Text(pluralStringResource(R.plurals.backup_imported, current.added, current.added), style = Hc.type.body, color = Hc.colors.ink)
            is BackupStatus.Problem -> Text(stringResource(current.message), style = Hc.type.body, color = Hc.colors.ink, modifier = Modifier)
        }
    }
}

private suspend fun importFrom(graph: AppGraph, uri: Uri, family: FamilyId, passphrase: String, context: android.content.Context): BackupStatus {
    val bytes = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: IOException) {
        null
    } ?: return BackupStatus.Problem(R.string.backup_corrupt)
    return when (val result = ExportCodec.decrypt(bytes, passphrase.toCharArray())) {
        ExportResult.WrongPassphrase -> BackupStatus.Problem(R.string.backup_wrong_passphrase)
        is ExportResult.Corrupt -> BackupStatus.Problem(R.string.backup_corrupt)
        is ExportResult.Ok -> when (val verdict = BackupModel.verdict(result.ops, family)) {
            ImportVerdict.Empty -> BackupStatus.Problem(R.string.backup_empty)
            ImportVerdict.OtherFamily -> BackupStatus.Problem(R.string.backup_other_family)
            is ImportVerdict.Merge -> BackupStatus.Imported(graph.opLog.append(result.ops).added)
        }
    }
}

private const val MIN_PASSPHRASE = 8
private const val MIME = "application/octet-stream"
