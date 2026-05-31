package xdman.util

class Base64 {
    companion object {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        @JvmStatic
        fun encode(bytes: ByteArray): String {
            var length = bytes.size
            if (length == 0) return ""
            val buffer = StringBuilder(
                Math.ceil(length.toDouble() / 3.0).toInt() * 4
            )
            val remainder = length % 3
            length -= remainder
            var block: Int
            var i = 0
            while (i < length) {
                block = ((bytes[i++].toInt() and 0xff) shl 16) or
                        ((bytes[i++].toInt() and 0xff) shl 8) or
                        (bytes[i++].toInt() and 0xff)
                buffer.append(ALPHABET[block ushr 18])
                buffer.append(ALPHABET[(block ushr 12) and 0x3f])
                buffer.append(ALPHABET[(block ushr 6) and 0x3f])
                buffer.append(ALPHABET[block and 0x3f])
            }
            if (remainder == 0) return buffer.toString()
            if (remainder == 1) {
                block = (bytes[i].toInt() and 0xff) shl 4
                buffer.append(ALPHABET[block ushr 6])
                buffer.append(ALPHABET[block and 0x3f])
                buffer.append("==")
                return buffer.toString()
            }
            block = (((bytes[i++].toInt() and 0xff) shl 8) or (bytes[i].toInt() and 0xff)) shl 2
            buffer.append(ALPHABET[block ushr 12])
            buffer.append(ALPHABET[(block ushr 6) and 0x3f])
            buffer.append(ALPHABET[block and 0x3f])
            buffer.append("=")
            return buffer.toString()
        }

        @JvmStatic
        fun decode(string: String): ByteArray {
            val length = string.length
            if (length == 0) return ByteArray(0)
            val pad = if (string[length - 2] == '=') 2 else if (string[length - 1] == '=') 1 else 0
            val size = length * 3 / 4 - pad
            val buffer = ByteArray(size)
            var block: Int
            var i = 0
            var index = 0
            while (i < length) {
                block = ((ALPHABET.indexOf(string[i++]) and 0xff) shl 18) or
                        ((ALPHABET.indexOf(string[i++]) and 0xff) shl 12) or
                        ((ALPHABET.indexOf(string[i++]) and 0xff) shl 6) or
                        (ALPHABET.indexOf(string[i++]) and 0xff)
                buffer[index++] = (block ushr 16).toByte()
                if (index < size) buffer[index++] = ((block ushr 8) and 0xff).toByte()
                if (index < size) buffer[index++] = (block and 0xff).toByte()
            }
            return buffer
        }
    }
}
