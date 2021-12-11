package net.starlegacy.feature.starship

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.starlegacy.SLComponent
import net.starlegacy.database.Oid
import net.starlegacy.database.generateOid
import net.starlegacy.database.schema.starships.Platform
import net.starlegacy.feature.multiblock.Multiblocks
import net.starlegacy.feature.multiblock.defenseturret.PlatformMultiblock
import net.starlegacy.feature.starship.active.ActivePlatform
import net.starlegacy.feature.starship.active.ActiveStarshipHitbox
import net.starlegacy.feature.starship.active.ActiveStarships
import net.starlegacy.feature.starship.active.SubsystemDetector
import net.starlegacy.util.*
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import java.util.*

object Platforms : SLComponent() {
    val CONNECTOR = Material.HAY_BLOCK
    private val LINKER = Material.LAPIS_BLOCK

    override fun onEnable() {
        for (data in Platform.all()) {
            activatePlatform(data)
        }
    }

    private fun activatePlatform(data: Platform) {
        if (Bukkit.getWorld(data.world) == null) {
            return
        }

        val blocks = LongOpenHashSet(data.blocks)

        val mass = data.mass

        val x = blockKeyX(data.blockKey)
        val y = blockKeyY(data.blockKey)
        val z = blockKeyZ(data.blockKey)
        val centerOfMass = Vec3i(x, y, z)

        val hitbox = ActiveStarshipHitbox(blocks)

        val starship = ActivePlatform(data, blocks, mass, centerOfMass, hitbox)
        SubsystemDetector.detectSubsystems(starship)
        ActiveStarships.add(starship)
    }

    @EventHandler
    fun onClickPlatformSign(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        val block = event.clickedBlock ?: return
        val sign = block.state as? Sign ?: return
        val multiblock = Multiblocks[sign] as? PlatformMultiblock ?: return
        val activated = multiblock.toggle(sign)

        if (activated) {
            activateSign(sign, multiblock)
            return
        }

        deactivateSign(sign)
    }

    @EventHandler
    fun onBreakSign(event: BlockBreakEvent) {
        ActiveStarships.all()
            .filterIsInstance<ActivePlatform>()
            .filter { it.data.blockKey == event.block.blockKey }
            .forEach {
                ActiveStarships.remove(it)
                val id = it.id

                Tasks.async {
                    Platform.remove(id)
                }
            }
    }

    private fun activateSign(sign: Sign, multiblock: PlatformMultiblock) {
        val id = generateOid<Platform>()

        sign.setLine(2, "&5&k$id".colorize())
        sign.update()

        val world = sign.world.name
        val blockKey = sign.block.blockKey
        val blocks = detectBlocks(sign, multiblock)
        val mass = 1.0
        val platform = Platform(id, world, blockKey, blocks, mass)

        activatePlatform(platform)

        Tasks.async {
            Platform.add(platform)
        }
    }

    private fun deactivateSign(sign: Sign) {
        val id = Oid<Platform>(sign.getLine(2).stripColor())

        val starship = ActiveStarships.getInWorld(sign.world)
            .filterIsInstance<ActivePlatform>()
            .firstOrNull { it.data.blockKey == sign.block.blockKey }

        if (starship != null) {
            ActiveStarships.remove(starship)
        }

        sign.setLine(2, "")
        sign.update()

        Tasks.async {
            Platform.remove(id)
        }
    }

    private fun detectBlocks(sign: Sign, multiblock: PlatformMultiblock): LongArray {
        val set = LongOpenHashSet(4096)

        val elements = multiblock.getBlocks(sign).map { it.blockKey }
        set.addAll(elements)

        val origin = PlatformMultiblock.getBaseConnector(sign)
        search(origin, set)

        return set.toLongArray()
    }

    private fun search(origin: Block, set: LongOpenHashSet) {
        // use a stack for depth-first search
        val open = Stack<Block>()

        // use a long open hash set for efficiency in checking if blocks are already closed
        // set to 4096 because it'll probably get quite big
        val closed = LongOpenHashSet(4096)

        // start with the origin
        open.push(origin)

        while (!open.empty()) {
            val block = open.pop()

            // ignore blocks that aren't connectors
            if (block.type != CONNECTOR) {
                if (block.type == LINKER) {
                    set.add(block.blockKey)
                    searchWeapons(block, set)
                }

                continue
            }

            set.add(block.blockKey)

            // add neighbors
            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        // ignore unloaded blocks
                        val neighbor = block.getRelativeIfLoaded(dx, dy, dz)
                            ?: continue

                        // adding to a set returns true if and only if it wasn't there before
                        val isNew = closed.add(neighbor.blockKey)

                        // ignore if it was already put in the closed set
                        if (!isNew) {
                            continue
                        }

                        // add the neighbor to the open stack
                        open.add(neighbor)
                    }
                }
            }
        }
    }

    private fun searchWeapons(linkerBlock: Block, set: LongOpenHashSet) {
        for (face1 in ADJACENT_BLOCK_FACES) {
            val relative = linkerBlock.getRelativeIfLoaded(face1)
                ?: continue
            val type = relative.type

            for (face2: BlockFace in CARDINAL_BLOCK_FACES) {
                val multiblock = SubsystemDetector.getWeaponMultiblock(relative, face2)
                    ?: continue

                val blocks = multiblock.getBlocks(relative, face2)
                val elements = blocks.map { it.blockKey }
                set.addAll(elements)

                // only add if at least one weapon is linked through it
                set.add(linkerBlock.blockKey)
            }
        }
    }
}
