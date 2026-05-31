package xdman

import xdman.downloaders.metadata.HttpMetadata

interface LinkRefreshCallback {
    fun getId(): String

    fun isValidLink(metadata: HttpMetadata): Boolean
}
