package net.starlegacy.feature.space

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import net.starlegacy.SLComponent
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent
import java.io.File
import java.util.*

object WorldFlags : SLComponent() {
    enum class Flag {
        SPACE,
        CIVILIZED
    }

    private fun getFlagFile(world: World, flag: Flag): File {
        return File(world.worldFolder, "data/starlegacy/${flag.name.toLowerCase()}.flag")
    }

    private val caches: Map<Flag, LoadingCache<World, Boolean>> = EnumMap(Flag.values().associateWith { flag ->
        CacheBuilder.newBuilder()
            .weakKeys()
            .build(CacheLoader.from { world -> world != null && getFlagFile(world, flag).exists() })
    })

    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        for (cache in caches.values) {
            cache.get(event.world)
        }
    }

    @EventHandler
    fun onWorldUnload(event: WorldUnloadEvent) {
        for (cache in caches.values) {
            cache.invalidate(event.world)
        }
    }

    fun setFlag(world: World, flag: Flag, value: Boolean) {
        val flagFile = getFlagFile(world, flag)

        if (value) {
            flagFile.parentFile.mkdirs()
            flagFile.createNewFile()
        } else {
            if (flagFile.exists()) {
                flagFile.delete()
            }
        }

        caches.getValue(flag).put(world, value)
    }

    @JvmStatic
    fun isFlagSet(world: World, flag: Flag): Boolean {
        return caches.getValue(flag).get(world)
    }

    @JvmStatic
    fun isCivilized(world: World): Boolean = isFlagSet(world, Flag.CIVILIZED)

    @JvmStatic
    fun isSpace(world: World): Boolean = isFlagSet(world, Flag.SPACE)

    override fun onDisable() {
        for (cache in caches.values) {
            cache.invalidateAll()
            cache.cleanUp()
        }
    }

    override fun supportsVanilla(): Boolean {
        return true
    }
}
