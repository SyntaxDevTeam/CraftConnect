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
    val type = readUnsignedByte()
    require(type == TAG_COMPOUND) { "Expected an inline NBT compound, got type $type" }
    return readNbtPayload(type) as NbtTag.CompoundTag
}

internal fun DataInputStream.readAnonymousNbt(): NbtTag = readNbtPayload(readUnsignedByte())

private fun DataInputStream.readNbtPayload(type: Int, depth: Int = 0): NbtTag {
    require(depth <= MAX_DEPTH) { "NBT is nested too deeply" }
    return when (type) {
        TAG_BYTE -> NbtTag.ByteTag(readByte())
        TAG_SHORT -> NbtTag.ShortTag(readShort())
        TAG_INT -> NbtTag.IntTag(readInt())
        TAG_LONG -> NbtTag.LongTag(readLong())
        TAG_FLOAT -> NbtTag.FloatTag(readFloat())
        TAG_DOUBLE -> NbtTag.DoubleTag(readDouble())
        TAG_BYTE_ARRAY -> NbtTag.ByteArrayTag(ByteArray(readSafeLength()).also(::readFully))
        TAG_STRING -> NbtTag.StringTag(readUTF())
        TAG_LIST -> {
            val elementType = readUnsignedByte()
            val size = readSafeLength()
            NbtTag.ListTag(List(size) {
                val entry = readNbtPayload(elementType, depth + 1)
                // Minecraft wraps heterogeneous list entries in a singleton compound
                // with an empty key. Unwrap exactly one layer, only inside a list.
                if (entry is NbtTag.CompoundTag && entry.value.size == 1 && entry.value.containsKey("")) {
                    entry.value.getValue("")
                } else {
                    entry
                }
            })
        }
        TAG_COMPOUND -> {
            val entries = linkedMapOf<String, NbtTag>()
            while (true) {
                val childType = readUnsignedByte()
                if (childType == TAG_END) break
                require(entries.size < MAX_COLLECTION_SIZE) { "NBT compound is too large" }
                entries[readUTF()] = readNbtPayload(childType, depth + 1)
            }
            NbtTag.CompoundTag(entries)
        }
        TAG_INT_ARRAY -> NbtTag.IntArrayTag(IntArray(readSafeLength()) { readInt() })
        TAG_LONG_ARRAY -> NbtTag.LongArrayTag(LongArray(readSafeLength()) { readLong() })
        else -> error("Unsupported NBT type $type")
    }
}

private fun DataInputStream.readSafeLength(): Int = readInt().also {
    require(it in 0..MAX_COLLECTION_SIZE) { "Invalid NBT collection length: $it" }
}

internal fun encodeLengthPrefixedStringCompound(values: Map<String, String>): ByteArray {
    val nbtBytes = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeByte(TAG_COMPOUND)
            values.forEach { (key, value) ->
                output.writeByte(TAG_STRING)
                output.writeUTF(key)
                output.writeUTF(value)
            }
            output.writeByte(TAG_END)
        }
        bytes.toByteArray()
    }
    return ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeVarInt(nbtBytes.size)
            output.write(nbtBytes)
        }
        bytes.toByteArray()
    }
}

private const val TAG_END = 0
private const val TAG_BYTE = 1
private const val TAG_SHORT = 2
private const val TAG_INT = 3
private const val TAG_LONG = 4
private const val TAG_FLOAT = 5
private const val TAG_DOUBLE = 6
private const val TAG_BYTE_ARRAY = 7
private const val TAG_STRING = 8
private const val TAG_LIST = 9
private const val TAG_COMPOUND = 10
private const val TAG_INT_ARRAY = 11
private const val TAG_LONG_ARRAY = 12
private const val MAX_DEPTH = 64
private const val MAX_COLLECTION_SIZE = 65_536
