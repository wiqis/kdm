package xdman.mediaconversion

import xdman.ui.res.StringResource
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

class FormatLoader {
    companion object {
        fun load(): List<FormatGroup> {
            val list = ArrayList<FormatGroup>()
            try {
                var inStream = StringResource::class.java.getResourceAsStream("/formats/format_db.txt")
                if (inStream == null) {
                    inStream = FileInputStream("formats/format_db.txt")
                }
                val r = InputStreamReader(inStream, Charset.forName("utf-8"))
                val br = BufferedReader(r)

                while (true) {
                    val ln = br.readLine() ?: break
                    if (ln.isEmpty()) break
                    val fg = FormatGroup()
                    val arr = ln.split("\\|".toRegex()).toTypedArray()
                    fg.name = arr[0].trim()
                    fg.desc = arr[1].trim()
                    println("group: " + fg.name)
                    list.add(fg)
                }
                while (true) {
                    val format = Format.read(br) ?: break
                    print(format)
                    for (fg in list) {
                        if (fg.name == format.group) {
                            println(fg.desc.toString() + " " + format.desc)
                            fg.formats.add(format)
                        }
                    }
                }
            } catch (e: Exception) {
            }
            return list
        }

        fun print(format: Format) {
            println("\t" + format.desc + " '" + format.group + "'")
            var list = format.videoCodecs
            if (!list.isNullOrEmpty()) {
                print("\t\tVideo Codec:")
                for (i in list.indices) {
                    if (list[i].length > 1) {
                        if (list[i] == format.defautVideoCodec) {
                            print("*")
                        }
                        print(list[i].toString() + " ")
                    }
                }
                println("\n")
            }

            list = format.resolutions
            if (!list.isNullOrEmpty()) {
                print("\t\tResolution:")
                for (i in list.indices) {
                    if (list[i].length > 1) {
                        if (list[i] == format.defaultResolution) {
                            print("*")
                        }
                        print(list[i].toString() + " ")
                    }
                }
                println("\n")
            }

            list = format.audioChannel
            if (!list.isNullOrEmpty()) {
                print("\t\tChannel:")
                for (i in list.indices) {
                    if (list[i] == format.defaultAudioChannel) {
                        print("*")
                    }
                    print(list[i].toString() + " ")
                }
                println("\n")
            }
        }
    }
}
