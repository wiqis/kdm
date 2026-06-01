package xdman.downloaders.metadata.manifests

import xdman.util.Base64
import xdman.util.Logger
import xdman.util.StringUtils
import java.io.FileReader
import java.net.URL
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import org.w3c.dom.Document
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

class F4MManifest(private var url: String, private var file: String) {
    private var fragTable: ArrayList<Fragment> = ArrayList()
    private var segTable: ArrayList<Segment> = ArrayList()
    var duration: Int = 0
    private var fromTimestamp: Long = 0
    private var start: Int = 0
    private var live: Boolean = false
    private var fragCount: Int = 0
    private var segStart: Int = 0
    private var fragStart: Int = 0
    private var fragsPerSeg: Int = 0
    private var selectedMedia: F4MMedia? = null
    private var segNum: Int = 0
    private var fragNum: Int = 0
    private var fragUrl: String = ""
    private var baseUrl: String = ""
    private var discontinuity: Int = 0
    private var query: String = ""
    private var pv: String = ""
    var selectedBitRate: Long = 0

    @Throws(Exception::class)
    fun getMediaUrls(): ArrayList<String> {
        val urlList = ArrayList<String>()
        fragTable = ArrayList()
        segTable = ArrayList()
        query = getQuery(url)
        parseDoc(loadDoc(file)!!, url)
        segNum = segStart
        fragNum = fragStart
        if (start > 0) {
            segNum = getSegmentFromFragment(start)
            fragNum = start - 1
            segStart = segNum
            fragStart = fragNum
        }
        Logger.log("$fragNum $fragCount")
        if (fragNum >= fragCount) throw Exception("No fragment available for downloading")
        Logger.log("[F4M Parser: selectedMedia.url: ${selectedMedia!!.url}")
        fragUrl = if (selectedMedia!!.url.startsWith("http")) {
            Logger.log("============ ${selectedMedia!!.url}")
            selectedMedia!!.url
        } else {
            if (baseUrl.endsWith("/")) baseUrl + selectedMedia!!.url else "$baseUrl/${selectedMedia!!.url}"
        }
        Logger.log("fragUrl: $fragUrl\nfragCount: $fragCount baseUrl: $baseUrl")
        while (fragNum < fragCount) {
            Logger.log("Remaining: ${fragCount - fragNum}")
            fragNum++
            segNum = getSegmentFromFragment(fragNum)
            val fragIndex = findFragmentInTable(fragNum)
            discontinuity = if (fragIndex >= 0) {
                fragTable[fragIndex].discontinuityIndicator
            } else {
                var disc = 0
                for (i in fragTable.indices) {
                    if (fragTable[i].firstFragment < fragNum) continue
                    disc = fragTable[i].discontinuityIndicator
                    break
                }
                disc
            }
            if (discontinuity != 0) {
                Logger.log("Skipping fragment $fragNum due to discontinuity, Type: $discontinuity")
                continue
            }
            var ___url = getFragmentUrl(segNum, fragNum)

            if (!StringUtils.isNullOrEmpty(query)) {
                ___url += if (___url.contains("?")) "&$query" else "?$query"
            }
            if (!StringUtils.isNullOrEmpty(pv)) {
                ___url += if (___url.contains("?")) "&$pv" else "?$pv"
            }
            Logger.log(___url)
            urlList.add(___url)
        }
        return urlList
    }

    @Throws(javax.xml.xpath.XPathExpressionException::class)
    private fun parseDoc(doc: Document, surl: String) {
        if (xpath == null) initXPath()
        baseUrl = xpath!!.evaluate("/ns:manifest/ns:baseURL", doc)
        if (StringUtils.isNullOrEmptyOrBlank(baseUrl)) {
            try {
                val url = URL(surl)
                val sb = StringBuilder()
                sb.append(url.protocol)
                sb.append("://")
                sb.append(url.host)
                var port = url.port
                if (port < 1) port = url.defaultPort
                sb.append(if (port == 80) "" else port)
                val path = url.path
                val arr = path.split("/")
                for (i in 0 until arr.size - 1) {
                    if (arr[i].isNotEmpty()) sb.append("/${arr[i]}")
                }
                baseUrl = sb.toString()
                Logger.log("*** URL: $baseUrl")
            } catch (_: Exception) {
            }
        }
        pv = xpath!!.evaluate("/ns:manifest/ns:pv-2.0", doc)
        val mediaNodeList = xpath!!.evaluate("/ns:manifest/ns:media", doc, XPathConstants.NODESET) as NodeList
        var media: F4MMedia? = null
        for (i in 0 until mediaNodeList.length) {
            val mediaNode = mediaNodeList.item(i)
            val attrMap = mediaNode.attributes
            val bitRateAttr = attrMap.getNamedItem("bitrate")
            val bitRate = if (bitRateAttr != null) bitRateAttr.nodeValue.toLong() else 0L
            var mediaFound = false
            if (selectedBitRate > 0) {
                if (selectedBitRate == bitRate) mediaFound = true
            } else {
                mediaFound = true
            }
            if (mediaFound) {
                media = F4MMedia()
                media.baseUrl = baseUrl
                media.bitRate = bitRate
                media.url = attrMap.getNamedItem("url").nodeValue
                val bootstrapInfoIdNode = attrMap.getNamedItem("bootstrapInfoId")
                val bootstrapInfoStr: String = if (bootstrapInfoIdNode != null) {
                    val bootstrapInfoId = bootstrapInfoIdNode.nodeValue
                    xpath!!.evaluate("/ns:manifest/ns:bootstrapInfo[@id='$bootstrapInfoId']", doc)
                } else {
                    xpath!!.evaluate("/ns:manifest/ns:bootstrapInfo", doc)
                }
                media.bootstrap = Base64.decode(bootstrapInfoStr)
                break
            }
        }
        if (media == null) {
            Logger.log("Could not find media")
            return
        }
        var pos = 0
        val ptr = BufferPointer()
        ptr.buf = media.bootstrap
        ptr.pos = pos
        val boxInfo = readBoxHeader(ptr)
        pos = ptr.pos
        val boxType = boxInfo.boxType
        if (boxType == "abst") parseBootstrapBox(media.bootstrap!!, pos)
        if (fragsPerSeg == 0) fragsPerSeg = fragCount
        live = if (live) {
            fromTimestamp = -1
            Logger.log("F4M Parser: [Live stream]")
            true
        } else {
            Logger.log("F4M Parser: [Not Live stream]")
            false
        }
        Logger.log("F4M Parser: Start- $start")
        selectedMedia = media
    }

    fun getBitRates(): LongArray? {
        try {
            if (xpath == null) initXPath()
            val doc = loadDoc(file)
            val mediaNodeList = xpath!!.evaluate("/ns:manifest/ns:media", doc, XPathConstants.NODESET) as NodeList
            if (mediaNodeList == null) return null
            val bitRates = ArrayList<Long>()
            for (i in 0 until mediaNodeList.length) {
                val mediaNode = mediaNodeList.item(i)
                val bitRateAttr = mediaNode.attributes.getNamedItem("bitrate")
                if (bitRateAttr != null) bitRates.add(bitRateAttr.nodeValue.toLong())
            }
            return bitRates.toLongArray()
        } catch (_: Exception) {
        }
        return null
    }

    private fun loadDoc(fileName: String): Document? {
        var r: FileReader? = null
        try {
            r = FileReader(fileName)
            val domFactory = DocumentBuilderFactory.newInstance()
            domFactory.isNamespaceAware = true
            val builder = domFactory.newDocumentBuilder()
            val doc = builder.parse(InputSource(r))
            return doc
        } catch (e: Exception) {
            Logger.log(e)
        } finally {
            if (r != null) {
                try {
                    r.close()
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    // keep the property as-is, already defined above

    private fun getSegmentFromFragment(fragN: Int): Int {
        if (segTable.isEmpty() || fragTable.isEmpty()) return 1
        val firstSegment = segTable[0]
        val lastSegment = segTable[segTable.size - 1]
        segTable[segTable.size - 1]
        if (segTable.size == 1) return firstSegment.firstSegment
        else {
            var prev = firstSegment
            var start = fragTable[0].firstFragment
            for (i in firstSegment.firstSegment..lastSegment.firstSegment) {
                val seg = if (segTable.size >= i - 1) segTable[i] else prev
                val end = start + seg.fragmentsPerSegment
                if (fragN >= start && fragN < end) return i
                prev = seg
                start = end
            }
        }
        return lastSegment.firstSegment
    }

    private fun parseBootstrapBox(bootstrapInfo: ByteArray, pos: Int) {
        Logger.log("parsing abst")
        live = false
        readByte(bootstrapInfo, pos)
        readInt24(bootstrapInfo, pos + 1)
        readInt32(bootstrapInfo, pos + 4)
        val b = readByte(bootstrapInfo, pos + 8)
        val update = (b and 0x10) shr 4
        if ((b and 0x20) shr 5 > 0) live = true
        if (update == 0) {
            segTable.clear()
            fragTable.clear()
        }
        readInt32(bootstrapInfo, pos + 9)
        readInt64(bootstrapInfo, 13)
        readInt64(bootstrapInfo, 21)
        var p = pos + 29
        val bPtr = BufferPointer()
        bPtr.buf = bootstrapInfo
        bPtr.pos = p
        readString(bPtr)
        p = bPtr.pos
        var serverEntryCount = readByte(bootstrapInfo, p++)
        bPtr.pos = p
        for (i in 0 until serverEntryCount) readString(bPtr)
        var qualityEntryCount = readByte(bootstrapInfo, p++)
        bPtr.pos = p
        for (i in 0 until qualityEntryCount) readString(bPtr)
        readString(bPtr)
        readString(bPtr)
        p = bPtr.pos
        val segRunTableCount = readByte(bootstrapInfo, p++)
        val ptr = BufferPointer()
        ptr.buf = bootstrapInfo
        for (i in 0 until segRunTableCount) {
            ptr.pos = p
            val boxInfo = readBoxHeader(ptr)
            val boxSize = boxInfo.boxSize
            val boxType = boxInfo.boxType
            p = ptr.pos
            if (boxType == "asrt") parseAsrtBox(bootstrapInfo, p)
            p += boxSize.toInt()
        }
        val fragRunTableCount = readByte(bootstrapInfo, p++)
        for (i in 0 until fragRunTableCount) {
            ptr.pos = p
            val boxInfo = readBoxHeader(ptr)
            p = ptr.pos
            val boxSize = boxInfo.boxSize
            val boxType = boxInfo.boxType
            Logger.log("555 $boxType $boxSize")
            if (boxType == "afrt") parseAfrtBox(bootstrapInfo, p)
            p += boxSize.toInt()
        }
        parseSegAndFragTable()
    }

    private fun parseSegAndFragTable() {
        Logger.log("parseSegAndFragTable called")
        if (segTable.isEmpty() || fragTable.isEmpty()) {
            Logger.log("return as zero ${segTable.size} ${fragTable.size}")
            return
        }
        val lastFragment = fragTable[fragTable.size - 1]
        if (lastFragment.fragmentDuration == 0 && lastFragment.discontinuityIndicator == 0) {
            live = false
            if (fragTable.isNotEmpty()) fragTable.removeAt(fragTable.size - 1)
        }
        val firstSegment = segTable[0]
        val lastSegment = segTable[segTable.size - 1]
        var prev = segTable[0]
        fragCount = prev.fragmentsPerSegment
        for (i in segTable.indices) {
            val current = segTable[i]
            fragCount += (current.firstSegment - prev.firstSegment - 1) * prev.fragmentsPerSegment
            fragCount += current.fragmentsPerSegment
            prev = current
        }
        var invalidFragCount = false
        if (fragCount and 0x80000000.toInt() == 0) fragCount += fragTable[0].firstFragment - 1
        if (fragCount and 0x80000000.toInt() != 0) {
            fragCount = 0
            invalidFragCount = true
        }
        if (fragCount < lastFragment.firstFragment) fragCount = lastFragment.firstFragment
        if (segStart < 0) {
            segStart = if (live) lastSegment.firstSegment else firstSegment.firstSegment
            if (segStart < 1) segStart = 1
        }
        if (fragStart < 0) {
            fragStart = if (live && !invalidFragCount) fragCount - 2 else fragTable[0].firstFragment - 1
            if (fragStart < 0) fragStart = 0
        }
    }

    private fun parseAsrtBox(asrt: ByteArray, pos: Int) {
        Logger.log("parsing asrt")
        readByte(asrt, pos)
        readInt24(asrt, pos + 1)
        val qualityEntryCount = readByte(asrt, pos + 4)
        segTable.clear()
        var p = pos + 5
        val bPtr = BufferPointer()
        for (i in 0 until qualityEntryCount) {
            bPtr.buf = asrt
            bPtr.pos = p
            readString(bPtr)
            p = bPtr.pos
        }
        val segCount = readInt32(asrt, p).toInt()
        p += 4
        Logger.log("segcount: $segCount")
        for (i in 0 until segCount) {
            val firstSegment = readInt32(asrt, p).toInt()
            val segEntry = Segment()
            segEntry.firstSegment = firstSegment
            segEntry.fragmentsPerSegment = readInt32(asrt, p + 4).toInt()
            if (segEntry.fragmentsPerSegment and 0x80000000.toInt() > 0) segEntry.fragmentsPerSegment = 0
            p += 8
            segTable.add(segEntry)
        }
    }

    private fun parseAfrtBox(afrt: ByteArray, pos: Int) {
        Logger.log("Parse afrt")
        fragTable.clear()
        readByte(afrt, pos)
        readInt24(afrt, pos + 1)
        readInt32(afrt, pos + 4)
        val qualityEntryCount = readByte(afrt, pos + 8)
        var p = pos + 9
        val args = BufferPointer()
        for (i in 0 until qualityEntryCount) {
            args.buf = afrt
            args.pos = p
            readString(args)
            p = args.pos
        }
        val fragEntries = readInt32(afrt, p).toInt()
        p += 4
        for (i in 0 until fragEntries) {
            val firstFragment = readInt32(afrt, p).toInt()
            val fragEntry = Fragment()
            fragEntry.firstFragment = firstFragment
            fragEntry.firstFragmentTimestamp = readInt64(afrt, p + 4)
            fragEntry.fragmentDuration = readInt32(afrt, p + 12).toInt()
            duration += fragEntry.fragmentDuration
            fragEntry.discontinuityIndicator = 0
            p += 16
            if (fragEntry.fragmentDuration == 0) fragEntry.discontinuityIndicator = readByte(afrt, p++)
            fragTable.add(fragEntry)
            if (fromTimestamp > 0 && fragEntry.firstFragmentTimestamp > 0 && fragEntry.firstFragmentTimestamp < fromTimestamp)
                start = fragEntry.firstFragment + 1
        }
    }

    private fun readBoxHeader(ptr: BufferPointer): BoxInfo {
        var pos = ptr.pos
        val bytesData = ptr.buf!!
        val boxType = StringBuilder()
        val boxSize: Long
        var boxSizeRaw = readInt32(bytesData, pos)
        boxType.append(readStringBytes(bytesData, pos + 4, 4))
        if (boxSizeRaw == 1L) {
            boxSize = readInt64(bytesData, pos + 8) - 16
            pos += 16
        } else {
            boxSize = boxSizeRaw - 8
            pos += 8
        }
        ptr.pos = pos
        val boxInfo = BoxInfo()
        boxInfo.boxSize = boxSize
        boxInfo.boxType = boxType.toString()
        return boxInfo
    }

    private fun readStringBytes(bytesData: ByteArray, pos: Int, len: Long): String {
        val resultValue = StringBuilder()
        for (i in 0 until len.toInt()) {
            resultValue.append((bytesData[pos + i].toInt() and 0xFF).toChar())
        }
        return resultValue.toString()
    }

    private fun readString(bufPtr: BufferPointer): String {
        val bytesData = bufPtr.buf!!
        var pos = bufPtr.pos
        val resultValue = StringBuilder()
        while (pos < bytesData.size && bytesData[pos] != 0.toByte()) {
            resultValue.append((bytesData[pos].toInt() and 0xFF).toChar())
            pos++
        }
        pos++
        bufPtr.pos = pos
        return resultValue.toString()
    }

    private fun readByte(data: ByteArray, pos: Int): Int = data[pos].toInt() and 0xFF

    private fun readInt24(data: ByteArray, pos: Int): Long {
        val iValLo = (data[pos + 2].toInt() and 0xFF).toLong() + ((data[pos + 1].toInt() and 0xFF) * 256)
        val iValHi = data[pos + 0].toInt() and 0xFF
        return iValLo + (iValHi * 65536)
    }

    companion object {
        private var xpath: XPath? = null

        private fun initXPath() {
            xpath = XPathFactory.newInstance().newXPath()
            xpath!!.setNamespaceContext(object : NamespaceContext {
                override fun getPrefixes(s: String): Iterator<String>? = null
                override fun getPrefix(s: String): String? = null
                override fun getNamespaceURI(s: String): String? {
                    return if ("ns" == s) "http://ns.adobe.com/f4m/1.0" else null
                }
            })
        }

        fun readInt32(data: ByteArray, pos: Int): Long {
            val iValLo = (data[pos + 3].toInt() and 0xFF).toLong() + (data[pos + 2].toInt() and 0xFF).toLong() * 256
            val iValHi = (data[pos + 1].toInt() and 0xFF).toLong() + ((data[pos + 0].toInt() and 0xFF).toLong() * 256)
            return iValLo + (iValHi * 65536)
        }

        fun readInt64(data: ByteArray, pos: Int): Long {
            val iValLo = readInt32(data, pos + 4)
            val iValHi = readInt32(data, pos)
            return iValLo + (iValHi * 4294967296L)
        }
    }

    private fun findFragmentInTable(needle: Int): Int {
        for (i in fragTable.indices) {
            if (fragTable[i].firstFragment == needle) return i
        }
        return -1
    }

    private fun getQuery(url: String): String {
        val index = url.indexOf('?')
        return if (index < 0) "" else url.substring(index + 1)
    }

    private fun getFragmentUrl(segNum: Int, fragNum: Int): String = "$fragUrl Seg$segNum-Frag$fragNum"

    inner class Segment {
        var firstSegment: Int = 0
        var fragmentsPerSegment: Int = 0
    }

    inner class Fragment {
        var firstFragment: Int = 0
        var firstFragmentTimestamp: Long = 0
        var fragmentDuration: Int = 0
        var discontinuityIndicator: Int = 0
    }

    inner class BoxInfo {
        var boxType: String = ""
        var boxSize: Long = 0
    }

    inner class BufferPointer {
        var buf: ByteArray? = null
        var pos: Int = 0
    }

    class F4MMedia {
        var baseUrl: String = ""
        var url: String = ""
        var bootstrapUrl: String = ""
        var bootstrap: ByteArray? = null
        var metadata: ByteArray? = null
        var bitRate: Long = 0
    }
}
