package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeVarInt

internal sealed interface NbtTag {
    data class ByteTag(val value: Byte) : NbtTag
    data class ShortTag(val value: Short) : NbtTag
    data class IntTag(val value: Int) : NbtTag
    data class LongTag(val value: Long) : NbtTag
    data class FloatTag(val value: Float) : NbtTag
    data class DoubleTag(val value: Double) : NbtTag
    data class ByteArrayTag(val value: ByteArray) : NbtTag
    data class StringTag(val value: String) : NbtTag
    data class ListTag(val value: List<NbtTag>) : NbtTag
    data class CompoundTag(val value: Map<String, NbtTag>) : NbtTag
    data class IntArrayTag(val value: IntArray) : NbtTag
    data class LongArrayTag(val value: LongArray) : NbtTag
}

internal fun DataInputStream.readInlineNbtCompound(): NbtTag.CompoundTag {
    require(readUnsignedByte() == COMPOUND) { "NBT root must be a compound" }
    return readPayload(COMPOUND) as NbtTag.CompoundTag
}

internal fun DataInputStream.readAnonymousNbt(): NbtTag = readPayload(readUnsignedByte())

private fun DataInputStream.readPayload(type: Int, depth: Int = 0): NbtTag {
    require(depth <= 64) { "NBT nesting too deep" }
    return when (type) {
    BYTE -> NbtTag.ByteTag(readByte())
    SHORT -> NbtTag.ShortTag(readShort())
    INT -> NbtTag.IntTag(readInt())
    LONG -> NbtTag.LongTag(readLong())
    FLOAT -> NbtTag.FloatTag(readFloat())
    DOUBLE -> NbtTag.DoubleTag(readDouble())
    BYTE_ARRAY -> NbtTag.ByteArrayTag(ByteArray(readSafeLength()).also(::readFully))
    STRING -> NbtTag.StringTag(readNbtString())
    LIST -> {
        val elementType = readUnsignedByte()
        val size = readSafeLength()
        NbtTag.ListTag(List(size) { readPayload(elementType, depth + 1) })
    }
    COMPOUND -> {
        val values = linkedMapOf<String, NbtTag>()
        while (true) {
            val childType = readUnsignedByte()
            if (childType == END) break
            values[readNbtString()] = readPayload(childType, depth + 1)
        }
        NbtTag.CompoundTag(values)
    }
    INT_ARRAY -> NbtTag.IntArrayTag(IntArray(readSafeLength()) { readInt() })
    LONG_ARRAY -> NbtTag.LongArrayTag(LongArray(readSafeLength()) { readLong() })
    else -> error("Unsupported NBT tag type $type")
}

}

private fun DataInputStream.readNbtString(): String {
    val size = readUnsignedShort()
    return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
}

private fun DataInputStream.readSafeLength(): Int = readInt().also {
    require(it in 0..MAX_COLLECTION_SIZE) { "Invalid NBT collection size" }
}

internal fun encodeLengthPrefixedStringCompound(values: Map<String, String>): ByteArray {
    val nbt = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeByte(COMPOUND)
            values.forEach { (key, value) ->
                output.writeByte(STRING)
                output.writeNbtString(key)
                output.writeNbtString(value)
            }
            output.writeByte(END)
        }
        bytes.toByteArray()
    }
    return ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeVarInt(nbt.size)
            output.write(nbt)
        }
        bytes.toByteArray()
    }
}

private fun DataOutputStream.writeNbtString(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    require(bytes.size <= 65_535)
    writeShort(bytes.size)
    write(bytes)
}

private const val END = 0
private const val BYTE = 1
private const val SHORT = 2
private const val INT = 3
private const val LONG = 4
private const val FLOAT = 5
private const val DOUBLE = 6
private const val BYTE_ARRAY = 7
private const val STRING = 8
private const val LIST = 9
private const val COMPOUND = 10
private const val INT_ARRAY = 11
private const val LONG_ARRAY = 12
private const val MAX_COLLECTION_SIZE = 65_536
