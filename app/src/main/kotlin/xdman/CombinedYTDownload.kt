package xdman

data class CombinedYTDownload(
    val combinedId: String,
    val title: String,
    val videoEntryId: String,
    val audioEntryId: String?,
    val videoExt: String,
    val audioExt: String?,
    val tempFolder: String,
    var mergedFilePath: String? = null,
    var mergeFailed: Boolean = false,
    val outputFolder: String
)
