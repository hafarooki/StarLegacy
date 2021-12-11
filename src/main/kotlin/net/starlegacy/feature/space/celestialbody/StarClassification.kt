package net.starlegacy.feature.space.celestialbody

import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Lightable

enum class StarClassification(
    val radius: Int,
    val surfaceData: List<BlockData>,
    val haloData: List<BlockData>,
) {
    RED_GIANT(
        radius = 125,
        surfaceData = listOf(
            Material.MAGMA_BLOCK.createBlockData(),
        ),
        haloData = listOf(
            Material.RED_STAINED_GLASS.createBlockData(),
            Material.ORANGE_STAINED_GLASS.createBlockData(),
        ),
    ),
    BLUE_GIANT(
        radius = 100,
        surfaceData = listOf(
            Material.SEA_LANTERN.createBlockData(),
        ),
        haloData = listOf(
            Material.CYAN_STAINED_GLASS.createBlockData(),
            Material.BLUE_STAINED_GLASS.createBlockData(),
        ),
    ),
    BROWN_DWARF(
        radius = 20,
        surfaceData = listOf(
            Material.MAGMA_BLOCK.createBlockData(),
            Material.BLACK_CONCRETE.createBlockData(),
        ),
        haloData = listOf(
            Material.BROWN_STAINED_GLASS.createBlockData(),
            Material.ORANGE_STAINED_GLASS.createBlockData(),
        ),
    ),
    MAIN_SEQUENCE_O(
        radius = 70,
        surfaceData = listOf(
            Material.SEA_LANTERN.createBlockData(),
        ),
        haloData = listOf(
            Material.CYAN_STAINED_GLASS.createBlockData(),
            Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(),
        ),
    ),
    MAIN_SEQUENCE_G(
        radius = 65,
        surfaceData = listOf(
            Material.GLOWSTONE.createBlockData(),
        ),
        haloData = listOf(
            Material.WHITE_STAINED_GLASS.createBlockData(),
            Material.YELLOW_STAINED_GLASS.createBlockData(),
        ),
    ),
    MAIN_SEQUENCE_M(
        radius = 30,
        surfaceData = listOf(
            Material.MAGMA_BLOCK.createBlockData(),
        ),
        haloData = listOf(
            Material.RED_STAINED_GLASS.createBlockData(),
            Material.BLACK_STAINED_GLASS.createBlockData(),
        ),
    ),
    MAIN_SEQUENCE_F(
        radius = 65,
        surfaceData = listOf(
            Material.SEA_LANTERN.createBlockData(),
        ),
        haloData = listOf(
            Material.WHITE_STAINED_GLASS.createBlockData(),
        ),
    ),
    MAIN_SEQUENCE_K(
        radius = 50,
        surfaceData = listOf(
            Material.SHROOMLIGHT.createBlockData(),
        ),
        haloData = listOf(
            Material.ORANGE_STAINED_GLASS.createBlockData(),
            Material.YELLOW_STAINED_GLASS.createBlockData(),
        ),
    ),
    MAIN_SEQUENCE_B(
        radius = 70,
        surfaceData = listOf(
            Material.SEA_LANTERN.createBlockData(),
        ),
        haloData = listOf(
            Material.PURPLE_STAINED_GLASS.createBlockData(),
            Material.BLUE_STAINED_GLASS.createBlockData(),
        ),
    ),
    NEUTRON_STAR(
        radius = 20,
        surfaceData = listOf(
            Material.SEA_LANTERN.createBlockData(),
        ),
        haloData = listOf(
            Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(),
            Material.BLUE_STAINED_GLASS.createBlockData(),
        ),
    ),
}
