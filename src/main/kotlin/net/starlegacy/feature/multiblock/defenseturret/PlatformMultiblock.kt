package net.starlegacy.feature.multiblock.defenseturret

import net.starlegacy.feature.multiblock.Multiblock
import net.starlegacy.feature.multiblock.MultiblockShape
import net.starlegacy.feature.starship.Platforms
import net.starlegacy.util.colorize
import net.starlegacy.util.getFacing
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Sign
import org.bukkit.entity.Player

object PlatformMultiblock : Multiblock() {
    private val DEACTIVATED_LINE = "&4[&cDeactivated&4]".colorize()
    private val ACTIVATED_LINE = "&2[&aActivated&2]".colorize()

    override val name: String = "platform"

    override val signText: List<String> = createSignText(
        "&cAutonomous",
        "&cPlatform",
        null,
        null,
    )

    override fun onTransformSign(player: Player, sign: Sign) {
        super.onTransformSign(player, sign)

        sign.setLine(3, DEACTIVATED_LINE)
        sign.update()
    }

    override fun MultiblockShape.buildStructure() {
        y(-1) {
            z(+0) {
                x(-1).anyStairs()
                x(+0).wireInput()
                x(+1).anyStairs()
            }

            z(+1) {
                x(-2).anyStairs()
                x(-1).anyStairs()
                x(+0).diamondBlock()
                x(+1).anyStairs()
                x(+2).anyStairs()
            }

            z(+2) {
                x(-2).ironBlock()
                x(-1).sponge()
                x(+0).diamondBlock()
                x(+1).sponge()
                x(+2).ironBlock()
            }
        }

        y(+0) {
            z(+0) {
                x(-1).anyGlassPane()
                x(+0).type(Material.OBSERVER)
                x(+1).anyGlassPane()
            }


            z(+1) {
                x(-2).ironBlock()
                x(-1).anyGlassPane()
                x(+0).redstoneLamp()
                x(+1).anyGlassPane()
                x(+2).ironBlock()
            }

            z(+2) {
                x(-2).ironBlock()
                x(-1).sponge()
                x(+0).type(Platforms.CONNECTOR)
                x(+1).sponge()
                x(+2).ironBlock()
            }
        }

        y(+1) {
            z(+0) {
                x(-1).anyStairs()
                x(+0).anyStairs()
                x(+1).anyStairs()
            }

            z(+1) {
                x(-2).anyStairs()
                x(-1).anyStairs()
                x(+0).redstoneBlock()
                x(+1).anyStairs()
                x(+2).anyStairs()
            }

            z(+2) {
                x(-2).ironBlock()
                x(-1).sponge()
                x(+0).sponge()
                x(+1).sponge()
                x(+2).ironBlock()
            }
        }
    }

    fun toggle(sign: Sign): Boolean {
        if (sign.getLine(3) == ACTIVATED_LINE) {
            sign.setLine(3, DEACTIVATED_LINE)
            sign.update()
            return false
        }

        sign.setLine(3, ACTIVATED_LINE)
        sign.update()
        return true
    }

    fun getBaseConnector(sign: Sign): Block {
        val inward = sign.getFacing().oppositeFace
        return sign.block.getRelative(inward, 3)
    }
}
