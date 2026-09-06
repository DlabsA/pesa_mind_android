package cc.dlabs.pesamind.features.settings.channels

/**
 * Which channel providers can have a statement imported, and in what format.
 *
 * Mirrors the backend's parser registry — a channel whose `channelDesc` is absent here is
 * rejected server-side with a typed 422 (`statement_import_unsupported`), so this table exists
 * to keep the button hidden and the file picker honest, never as the authority. When the backend
 * gains a parser, add the provider here too.
 *
 * The provider names come from [ChannelDescBank]/[ChannelDescMobileMoney] rather than string
 * literals so a rename can't silently desync the two.
 */
object StatementImportSupport {
    private const val PDF = "pdf"
    private const val CSV = "csv"

    /** Extensions accepted per provider. Only Stanbic exports a machine-readable CSV; the two
     *  mobile-money providers only publish PDF statements. */
    private val ACCEPTED_EXTENSIONS: Map<String, Set<String>> =
        mapOf(
            ChannelDescBank.STANBIC_BANK to setOf(PDF, CSV),
            ChannelDescMobileMoney.AIRTELMONEY to setOf(PDF),
            ChannelDescMobileMoney.MTNMOBILEMONEY to setOf(PDF),
        )

    /** Mime types handed to `ActivityResultContracts.OpenDocument`. CSV gets several because
     *  document providers disagree about what to report for one — the extension check in
     *  `validateUpload` is what actually decides. */
    private val CSV_MIME_TYPES =
        arrayOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "text/plain")

    fun isSupported(channelDesc: String?): Boolean = normalize(channelDesc) != null

    fun allowedExtensions(channelDesc: String?): Set<String> = ACCEPTED_EXTENSIONS[normalize(channelDesc)] ?: setOf(PDF)

    fun pickerMimeTypes(channelDesc: String?): Array<String> =
        if (CSV in allowedExtensions(channelDesc)) {
            arrayOf("application/pdf") + CSV_MIME_TYPES
        } else {
            arrayOf("application/pdf")
        }

    /** Human-readable "PDF" / "PDF or CSV", for the explainer copy on the import screen. */
    fun acceptedFormatsLabel(channelDesc: String?): String = allowedExtensions(channelDesc).sorted().joinToString(" or ") { it.uppercase() }

    private fun normalize(channelDesc: String?): String? {
        val trimmed = channelDesc?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return ACCEPTED_EXTENSIONS.keys.firstOrNull { it.equals(trimmed, ignoreCase = true) }
    }
}
