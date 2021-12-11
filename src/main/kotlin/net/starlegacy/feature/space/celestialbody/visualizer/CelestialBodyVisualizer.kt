package net.starlegacy.feature.space.celestialbody.visualizer

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketEvent
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import net.starlegacy.SLComponent
import net.starlegacy.cache.space.*
import net.starlegacy.database.schema.space.CelestialBody
import net.starlegacy.util.*
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object CelestialBodyVisualizer : SLComponent() {
    private val cache = PerWorld { world ->
        CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build<Long, CachedChunkData>(CacheLoader.from { chunkKey -> getChunkData(world, checkNotNull(chunkKey)) })
    }

    private val pool = Executors.newSingleThreadExecutor(Tasks.namedThreadFactory("celestial-body-visualizer"))

    override fun onEnable() {
        addPacketSendListener(
            ListenerPriority.HIGHEST, PacketType.Play.Server.MAP_CHUNK,
            CelestialBodyVisualizer::onSendChunk
        )
    }

    override fun onDisable() {
        pool.shutdownNow()
    }

    private fun onSendChunk(event: PacketEvent) {
        if (event.isCancelled) {
            return
        }

        val packet = event.packet
        val chunkX = packet.integers.read(0)
        val chunkZ = packet.integers.read(1)
        val chunkKey = chunkKey(chunkX, chunkZ)

        val fullChunk = packet.booleans.read(0)
        val bitmask = if (fullChunk) (1 shl 16).inv() else packet.integers.read(2)

        val player = event.player
        val world = player.world

        loadAndSend(chunkKey, world, player, bitmask)
    }

    private fun loadAndSend(
        chunkKey: Long,
        world: World,
        player: Player,
        bitmask: Int
    ) {
        pool.submit {
            val chunkData = cache[world][chunkKey]

            if (chunkData.map.isEmpty()) {
                return@submit
            }

            Tasks.sync {
                send(world, chunkKey, player, chunkData, bitmask)
            }
        }
    }

    private fun send(
        world: World,
        chunkKey: Long,
        player: Player,
        chunkData: CachedChunkData,
        bitmask: Int
    ) {
        /*  Note that even if this were not async -> sync, it would still
            need to be sent after the initial packet
            for it not to be overridden by the initial chunk packet.    */
        val chunk = world.getChunkAtIfLoaded(chunkKey) ?: return

        val playerChunkMap = chunk.nms.world.chunkProvider.playerChunkMap
        val playerViewDistanceBroadcastMap = playerChunkMap.playerViewDistanceBroadcastMap
        val objectsInRange = playerViewDistanceBroadcastMap.getObjectsInRange(chunk.nms.coordinateKey)

        if (objectsInRange?.contains(player.nms) != true) {
            return
        }

        for ((sectionY, sectionData) in chunkData.map) {
            if (!isBitSet(bitmask, sectionY)) {
                continue
            }

            val packet = sectionData.packet
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet)
        }
    }

    private fun isBitSet(integer: Int, bitIndex: Int): Boolean {
        return ((integer shr bitIndex) and 1) == 1
    }

    private fun getChunkData(world: World, chunkKey: Long): CachedChunkData {
        val builder = ChunkCacheBuilder(world, chunkKey)
        fillChunk(builder, StarCache)
        fillChunk(builder, PlanetCache)
        fillChunk(builder, CosmicBarrierCache)
        fillChunk(builder, BlackHoleCache)
        return builder.buildData()
    }

    private fun fillChunk(builder: ChunkCacheBuilder, cache: AbstractCelestialBodyCache<out CelestialBody>) {
        for (celestialBody in cache.getAll()) {
            val visualizer = celestialBody.instance

            if (!visualizer.isChunkInRange(builder.world, builder.chunkX, builder.chunkZ)) {
                continue
            }

            visualizer.populateChunk(builder)
        }
    }
}
