package cc.dlabs.pesamind.core.data

import android.util.Log
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.ApiService
import cc.dlabs.pesamind.core.network.models.StatementImportSummary
import cc.dlabs.pesamind.core.utils.PickedFile
import cc.dlabs.pesamind.core.utils.toMultipartPart
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of one statement upload.
 *
 * [NeedsAccountSync] is a *success* HTTP-wise: the transactions were imported, but the
 * statement's account number differs from the channel's, so the server held back the balance
 * update and is waiting for the user to confirm before overwriting the channel's number.
 */
sealed interface ImportOutcome {
    data class Success(val summary: StatementImportSummary) : ImportOutcome

    data class NeedsAccountSync(val summary: StatementImportSummary) : ImportOutcome

    data class Failure(val message: String, val code: String? = null) : ImportOutcome
}

/**
 * Uploads a bank/mobile-money statement and folds the server's work back into Room.
 *
 * This crosses the repository threshold described in `.claude/CLAUDE.md` — it isn't a thin
 * wrapper over one endpoint but a merge of three sources: the multipart upload, a targeted
 * transaction pull, and a channel reconcile. The transactions the server creates exist nowhere
 * locally until that pull runs, so skipping it would leave the user staring at a stale balance
 * and an unchanged list right after a "74 imported" summary.
 *
 * Constructor-injected (`NetworkModule.provideApiService` already binds [ApiService]) rather than
 * following the `object` + `init(context)` pattern of its neighbours, which predate Hilt here.
 */
@Singleton
class StatementImportRepository
    @Inject
    constructor(
        private val api: ApiService,
    ) {
        /**
         * [channelId] is the **local** Room id, as held by every screen; the server id is resolved
         * internally. [confirmAccountSync] re-runs an import the user has confirmed after an
         * account mismatch — safe to repeat, since the backend dedups: the second call reports
         * everything as duplicates.
         */
        suspend fun import(
            channelId: String,
            file: PickedFile,
            confirmAccountSync: Boolean = false,
        ): ImportOutcome {
            val serverId =
                ChannelRepository.serverIdFor(channelId)
                    ?: return ImportOutcome.Failure(
                        "This account hasn't finished syncing — try again once you're online.",
                    )

            val response =
                try {
                    api.importStatement(
                        channelId = serverId,
                        file = file.toMultipartPart(),
                        // Omit the param entirely rather than sending false, so the request is
                        // byte-identical to a plain first upload.
                        confirmAccountSync = confirmAccountSync.takeIf { it },
                    )
                } catch (e: Exception) {
                    Log.w("StatementImport", "Upload failed", e)
                    return ImportOutcome.Failure("Cannot reach server: ${e.message ?: "Unknown error"}")
                }

            if (!response.isSuccessful) {
                val (message, code) = parseStatementImportError(response.code(), response.errorBodyText())
                return ImportOutcome.Failure(message, code)
            }

            val summary = response.body() ?: return ImportOutcome.Failure("The server returned an empty response.")

            // Rows land locally in both branches — an account mismatch withholds the balance
            // update, not the import itself.
            refreshAfterImport(channelId, serverId)

            return if (summary.accountSyncRequired) {
                ImportOutcome.NeedsAccountSync(summary)
            } else {
                ImportOutcome.Success(summary)
            }
        }

        /**
         * Pull what the server just created into Room: the channel's transactions, then the
         * channel row itself (for the recomputed balance). Best-effort by design — a failed
         * refresh leaves stale local data, which the next sync fixes, and must never downgrade a
         * successful import into an error the user reads as "it didn't work".
         */
        private suspend fun refreshAfterImport(
            channelId: String,
            serverId: String,
        ) {
            try {
                TransactionRepository.refreshByChannel(channelId)
            } catch (e: Exception) {
                Log.w("StatementImport", "Post-import transaction refresh failed", e)
            }
            try {
                // No GET /categories/:id exists, so this mirrors SyncWorker.pullChannels' pull —
                // minus its server-side-deletion sweep, which has no business running here.
                val channels = ApiClient.api.getChannels()
                if (channels.isSuccessful) {
                    channels.body()
                        ?.firstOrNull { it.id == serverId }
                        ?.let { ChannelRepository.reconcileFromServer(it) }
                }
            } catch (e: Exception) {
                Log.w("StatementImport", "Post-import channel refresh failed", e)
            }
        }
    }

private fun Response<*>.errorBodyText(): String? =
    try {
        errorBody()?.string()
    } catch (e: Exception) {
        Log.w("StatementImport", "Failed to read error body", e)
        null
    }

/**
 * Turn a non-2xx statement-import response into (user-facing message, machine code).
 *
 * Top-level and pure so the whole error table is unit-testable without a server.
 * `AuthViewModel.extractErrorFromResponse` is deliberately not reused: it's private and only
 * reads `error`, whereas this contract also carries `code` and, for one case, `channel_desc`.
 */
internal fun parseStatementImportError(
    httpCode: Int,
    errorBody: String?,
): Pair<String, String?> {
    val fields = parseErrorFields(errorBody)
    val error = fields["error"]
    val code = fields["code"]

    // The odd one out: this response has no `code` field at all — the machine code *is* the
    // `error` value, and the provider name rides along in `channel_desc`.
    if (error == UNSUPPORTED_CODE) {
        val provider = fields["channel_desc"]?.takeIf { it.isNotBlank() } ?: "this account"
        return "Statement import isn't supported for $provider yet." to UNSUPPORTED_CODE
    }

    val message =
        when (code) {
            "EMPTY_FILE" -> "That file is empty."
            "NOT_FOUND" -> "Account not found."
            "FORBIDDEN" -> "You don't have access to this account."
            "CHANNEL_DESC_NOT_SET" -> "This account has no provider set — edit it and choose one."
            "STATEMENT_FORMAT_NOT_ACCEPTED" -> "This account only accepts PDF statements."
            "STATEMENT_CONTENT_MISMATCH" -> "This file doesn't look like a statement for this account."
            "STATEMENT_PARSE_FAILED" -> "Couldn't read that statement file."
            "STATEMENT_EMPTY" -> "No transactions were found in that statement."
            else ->
                when (httpCode) {
                    413 -> "That file is too large (max 10 MB)."
                    401 -> "Your session expired — sign in again."
                    in 500..599 -> "The server had a problem reading that statement. Try again shortly."
                    else -> error?.takeIf { it.isNotBlank() } ?: "Import failed (HTTP $httpCode)."
                }
        }
    return message to code
}

private const val UNSUPPORTED_CODE = "statement_import_unsupported"

/**
 * Lenient string-valued view of the error body — a body that isn't JSON at all (an HTML error
 * page from a proxy, say) yields nothing rather than throwing, so the HTTP-code fallback above
 * still produces a usable message. Deliberately free of `android.util.Log` so the whole parse
 * path stays callable from a plain JVM test.
 */
private fun parseErrorFields(errorBody: String?): Map<String, String> {
    if (errorBody.isNullOrBlank()) return emptyMap()
    return try {
        @Suppress("UNCHECKED_CAST")
        val raw = Gson().fromJson(errorBody, Map::class.java) as? Map<Any?, Any?> ?: return emptyMap()
        raw.entries
            .mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? String ?: return@mapNotNull null
                key to value
            }.toMap()
    } catch (_: JsonSyntaxException) {
        emptyMap()
    }
}
