package xdman.mediaconversion

class ConversionItem {
    var inputFileName: String? = null
    var inputFile: String? = null
    var outFileName: String? = null
    var outFolder: String? = null
    var info: MediaFormatInfo? = null
    var targetFormat: MediaFormat? = null
    var volume: String? = null
    var conversionState: Int = 0
}
