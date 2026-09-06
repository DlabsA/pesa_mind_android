package cc.dlabs.pesamind.core.utils

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** 10 MiB — matches the backend's `maxStatementBytes`, so an oversize file is rejected here
 *  with a readable message instead of costing the user an upload and a 413. */
const val MAX_UPLOAD_BYTES: Long = 10L * 1024 * 1024

/**
 * A file the user picked through the Storage Access Framework, already read into memory.
 *
 * The bytes are held eagerly and deliberately: `TokenRefreshInterceptor` replays a request once
 * after refreshing an expired token, and a `RequestBody` backed by a consumed `InputStream`
 * cannot be replayed — the retry would upload zero bytes. Files are capped at
 * [MAX_UPLOAD_BYTES], so the memory cost is bounded.
 *
 * Note the generated `equals`/`hashCode` compare `bytes` by identity. Nothing compares instances
 * today; if that changes, write them by hand.
 */
data class PickedFile(
    val bytes: ByteArray,
    val displayName: String,
    val mimeType: String,
)

sealed interface PickedFileResult {
    data class Ok(val file: PickedFile) : PickedFileResult

    data class Error(val message: String) : PickedFileResult
}

/** Extension → the mime we *send*. Deliberately not `ContentResolver.getType(uri)`: SAF
 *  routinely reports a CSV as `text/plain` or `application/octet-stream` depending on the
 *  provider app, and the server keys its parser off the filename extension anyway. */
private val MIME_BY_EXTENSION =
    mapOf(
        "pdf" to "application/pdf",
        "csv" to "text/csv",
    )

/** The file extension, lowercased and without the dot; empty when the name has none. */
fun String.fileExtension(): String = substringAfterLast('.', "").lowercase()

/**
 * Validate a display name + byte count against an upload's rules, returning a user-facing
 * message or null when it passes. Pure, so it is unit-testable without a `ContentResolver`.
 */
fun validateUpload(
    displayName: String,
    sizeBytes: Long,
    allowedExtensions: Set<String>,
    maxBytes: Long = MAX_UPLOAD_BYTES,
): String? {
    val extension = displayName.fileExtension()
    return when {
        sizeBytes <= 0L -> "That file is empty."
        sizeBytes > maxBytes -> "That file is too large (max ${maxBytes / (1024 * 1024)} MB)."
        extension.isBlank() ->
            "Couldn't tell what kind of file that is — pick a ${allowedExtensions.readableList()} file."
        extension !in allowedExtensions ->
            "This account only accepts ${allowedExtensions.readableList()} files."
        else -> null
    }
}

private fun Set<String>.readableList(): String = sorted().joinToString(" or ") { it.uppercase() }

/**
 * Read a SAF [uri] into a [PickedFile], validating it along the way. Blocking — call from
 * `Dispatchers.IO`.
 */
fun ContentResolver.readPickedFile(
    uri: Uri,
    allowedExtensions: Set<String>,
    maxBytes: Long = MAX_UPLOAD_BYTES,
): PickedFileResult {
    val displayName =
        queryDisplayName(uri)
            ?: return PickedFileResult.Error("Couldn't read that file — try picking it again.")

    val bytes =
        try {
            openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.w("FileUpload", "Failed to read picked file", e)
            null
        } ?: return PickedFileResult.Error("Couldn't read that file — try picking it again.")

    validateUpload(displayName, bytes.size.toLong(), allowedExtensions, maxBytes)
        ?.let { return PickedFileResult.Error(it) }

    val mimeType = MIME_BY_EXTENSION[displayName.fileExtension()] ?: "application/octet-stream"
    return PickedFileResult.Ok(PickedFile(bytes = bytes, displayName = displayName, mimeType = mimeType))
}

private fun ContentResolver.queryDisplayName(uri: Uri): String? =
    try {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.w("FileUpload", "Failed to query display name", e)
        null
    }

/**
 * Wrap the file as one multipart part. The [PickedFile.displayName] is sent verbatim, extension
 * included — the backend chooses its statement parser from that extension, so stripping or
 * renaming it silently breaks the import.
 */
fun PickedFile.toMultipartPart(partName: String = "file"): MultipartBody.Part =
    MultipartBody.Part.createFormData(
        partName,
        displayName,
        bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
    )
