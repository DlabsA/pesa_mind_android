package cc.dlabs.pesamind.features.settings.channels.statementimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.ChannelRepository
import cc.dlabs.pesamind.core.data.ImportOutcome
import cc.dlabs.pesamind.core.data.StatementImportRepository
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.network.models.StatementImportSummary
import cc.dlabs.pesamind.core.utils.PickedFile
import cc.dlabs.pesamind.core.utils.PickedFileResult
import cc.dlabs.pesamind.core.utils.readPickedFile
import cc.dlabs.pesamind.features.settings.channels.StatementImportSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImportStatementState(
    val channel: ChannelDetails? = null,
    val isUploading: Boolean = false,
    val pickedName: String? = null,
    val result: StatementImportSummary? = null,
    // Non-null while the account-mismatch confirmation is pending — the rows are already
    // imported at this point; only the channel balance is waiting on the user's answer.
    val pendingSync: StatementImportSummary? = null,
    val error: String? = null,
)

/**
 * Backs `ImportStatementScreen`. The picked file's bytes live in a private field rather than in
 * [ImportStatementState] — up to 10 MiB has no business inside a state object that every
 * recomposition compares.
 */
@HiltViewModel
class ImportStatementViewModel
    @Inject
    constructor(
        private val repo: StatementImportRepository,
        @ApplicationContext private val appContext: Context,
    ) : UnifiedViewModel() {
        private val _state = MutableStateFlow(ImportStatementState())
        val state: StateFlow<ImportStatementState> = _state.asStateFlow()

        private var channelId: String? = null
        private var pickedFile: PickedFile? = null

        fun load(channelId: String) {
            this.channelId = channelId
            viewModelScope.launch {
                _state.value = _state.value.copy(channel = ChannelRepository.getById(channelId))
            }
        }

        fun onFilePicked(uri: Uri) {
            val channelDesc = _state.value.channel?.channelDesc
            viewModelScope.launch {
                val outcome =
                    withContext(Dispatchers.IO) {
                        appContext.contentResolver.readPickedFile(
                            uri = uri,
                            allowedExtensions = StatementImportSupport.allowedExtensions(channelDesc),
                        )
                    }
                when (outcome) {
                    is PickedFileResult.Ok -> {
                        pickedFile = outcome.file
                        // A new file invalidates any summary still on screen from a previous run.
                        _state.value =
                            _state.value.copy(
                                pickedName = outcome.file.displayName,
                                result = null,
                                error = null,
                            )
                    }
                    is PickedFileResult.Error -> {
                        pickedFile = null
                        _state.value = _state.value.copy(pickedName = null, error = outcome.message)
                    }
                }
            }
        }

        fun upload(confirm: Boolean = false) {
            val id = channelId ?: return
            val file = pickedFile ?: return
            viewModelScope.launch {
                _state.value = _state.value.copy(isUploading = true, error = null)
                when (val outcome = repo.import(channelId = id, file = file, confirmAccountSync = confirm)) {
                    is ImportOutcome.Success -> {
                        _state.value =
                            _state.value.copy(isUploading = false, result = outcome.summary, pendingSync = null)
                        announceImport(id)
                    }
                    is ImportOutcome.NeedsAccountSync -> {
                        // Rows landed; only the balance is held back. Refresh the rest of the app
                        // either way, then ask — unless the server flagged the sync without
                        // telling us which two numbers disagree, in which case there's no
                        // question worth putting to the user; show the summary instead of a
                        // dialog with blanks in it.
                        _state.value =
                            if (outcome.summary.accountMismatch == null) {
                                _state.value.copy(isUploading = false, result = outcome.summary, pendingSync = null)
                            } else {
                                _state.value.copy(isUploading = false, pendingSync = outcome.summary)
                            }
                        announceImport(id)
                    }
                    is ImportOutcome.Failure ->
                        _state.value = _state.value.copy(isUploading = false, error = outcome.message)
                }
            }
        }

        fun confirmAccountSync() = upload(confirm = true)

        /** Keep the transactions, decline the balance/account-number update. */
        fun dismissSync() {
            val summary = _state.value.pendingSync ?: return
            _state.value = _state.value.copy(pendingSync = null, result = summary)
        }

        fun clearError() {
            _state.value = _state.value.copy(error = null)
        }

        /**
         * The repository has already reconciled the new rows into Room; these events are what
         * tell the screens currently holding stale copies to re-read. `ChannelUpdated` is what
         * `ChannelDetailViewModel` listens for to refresh the balance on the screen underneath.
         */
        private fun announceImport(id: String) {
            publishEvent(
                StateEvent.ChannelUpdated(
                    channelId = id,
                    channelName = _state.value.channel?.name.orEmpty(),
                ),
            )
            publishEvent(StateEvent.TransactionsRefreshed)
        }
    }
