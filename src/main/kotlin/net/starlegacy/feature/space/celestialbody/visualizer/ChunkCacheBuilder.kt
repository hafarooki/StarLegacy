package net.starlegacy.feature.space.celestialbody.visualizer

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.BlockPosition
import com.comphenix.protocol.wrappers.WrappedBlockData
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap
import net.starlegacy.util.chunkKeyX
import net.starlegacy.util.chunkKeyZ
import org.bukkit.World
import org.bukkit.block.data.BlockData

class ChunkCacheBuilder(
    val world: World,
    val chunkKey: Long
) {
    val chunkX = chunkKeyX(chunkKey)
    val chunkZ = chunkKeyZ(chunkKey)

    // section y -> local (within section) block key -> block data
    private val sectionMaps: Map<Int, MutableMap<Short, BlockData>> =
        (0..15).associateWith { Short2ObjectOpenHashMap() }

    fun setBlockGlobal(x: Int, y: Int, z: Int, blockData: BlockData) {
        check(x shr 4 == chunkX)
        check(z shr 4 == chunkZ)

        setBlockLocal(x and 15, y, z and 15, blockData)
    }

    fun setBlockLocal(chunkLocalX: Int, y: Int, chunkLocalZ: Int, blockData: BlockData) {
        check(chunkLocalX in 0..15)
        check(y in 0..255)
        check(chunkLocalZ in 0..15)

        val sectionY = y shr 4
        val sectionLocalY = y and 15
        val localKey = (chunkLocalX shl 8 or (chunkLocalZ shl 4) or sectionLocalY).toShort()
        val sectionMap = sectionMaps.getValue(sectionY)
        sectionMap[localKey] = blockData
    }

    fun buildData(): CachedChunkData {
        val map = this.buildMap()
        return CachedChunkData(this.chunkKey, map)
    }

    private fun buildMap(): Map<Int, CachedSectionData> {
        return sectionMaps
            .filter { it.value.any() } // exclude empty ones
            .mapValues { (sectionY, blockMap) ->
                val packet = createPacket(chunkKey, sectionY, blockMap)
                return@mapValues CachedSectionData(chunkKey, sectionY, packet)
            }
    }

    private fun createPacket(
        chunkKey: Long,
        sectionY: Int,
        blockMap: Map<Short, BlockData>
    ): PacketContainer {
        val chunkX = chunkKeyX(chunkKey)
        val chunkZ = chunkKeyZ(chunkKey)

        val alwaysInverseTrustedEdgesBoolOfPrecedingUpdateLightPacket = true

        val blockDataList = mutableListOf<WrappedBlockData>()
        val blockKeyList = mutableListOf<Short>()

        for ((key, data) in blockMap) {
            blockKeyList.add(key)
            blockDataList.add(WrappedBlockData.createData(data))
        }

        val packet = PacketContainer(PacketType.Play.Server.MULTI_BLOCK_CHANGE)
        packet.sectionPositions.write(0, BlockPosition(chunkX, sectionY, chunkZ))
        packet.booleans.write(0, alwaysInverseTrustedEdgesBoolOfPrecedingUpdateLightPacket)
        packet.blockDataArrays.writeSafely(0, blockDataList.toTypedArray())
        packet.shortArrays.writeSafely(0, blockKeyList.toShortArray())
        return packet
    }
}
