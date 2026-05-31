package xdman.mediaconversion

import xdman.ui.res.StringResource

class MediaFormat {
    var format: String? = null
    var description: String? = null
    var audioOnly: Boolean = false

    var resolution: String? = null
    var video_codec: String? = null
    var video_bitrate: String? = null
    var framerate: String? = null
    var video_param_extra: String? = null
    var audio_codec: String? = null
    var audio_bitrate: String? = null
    var samplerate: String? = null
    var audio_extra_param: String? = null
    var audio_channel: String? = null
    var aspectRatio: String? = null

    override fun toString(): String {
        if (format == null) {
            return StringResource.get("VID_FMT_ORIG") ?: ""
        }
        return "$format $description"
    }
}
