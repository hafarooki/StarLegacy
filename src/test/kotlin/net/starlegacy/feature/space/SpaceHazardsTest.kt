package net.starlegacy.feature.space

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import be.seeseemelk.mockbukkit.WorldMock
import be.seeseemelk.mockbukkit.entity.PlayerMock
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import net.starlegacy.StarLegacy
import net.starlegacy.cache.space.StarCache
import net.starlegacy.database.schema.space.Star
import net.starlegacy.feature.space.celestialbody.StarClassification
import net.starlegacy.feature.space.celestialbody.instance.StarInstance
import net.starlegacy.feature.space.hazard.SpaceHazards
import net.starlegacy.feature.starship.active.ActivePlayerStarship
import net.starlegacy.feature.starship.active.ActiveStarships
import net.starlegacy.feature.starship.subsystem.shield.SphereShieldSubsystem
import net.starlegacy.util.Vec3i
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class SpaceHazardsTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: StarLegacy
    private lateinit var world: WorldMock
    private lateinit var player: PlayerMock

    @BeforeEach
    fun beforeEach() {
        server = MockBukkit.mock()
        plugin = mockk()
        every { plugin.name } returns "StarLegacy"
        val namespaceKeyParam = CapturingSlot<String>()
        every { plugin.namespacedKey(capture(namespaceKeyParam)) }.answers {
            NamespacedKey(plugin, namespaceKeyParam.captured)
        }
        StarLegacy.PLUGIN = plugin
        world = server.addSimpleWorld("test")
        player = server.addPlayer()
        val location = Location(world, 0.0, 64.0, 0.0)
        player.location = location
    }

    @AfterEach
    fun afterEach() {
        MockBukkit.unmock()
    }

    @Test
    fun applyStarHazardsAffectsPlayersNearStars() {
        mockkObject(StarCache)
        every { StarCache.getAll() } returns listOf(mockk<Star>().also { star ->
            every { star.classification } answers { StarClassification.MAIN_SEQUENCE_G }
            every { star.instance } answers {
                mockk<StarInstance>().also { instance ->
                    every { instance.spaceWorld } answers { player.world }
                    every { instance.position } answers { Vec3i(player.location) }
                }
            }
        })

        mockkObject(ActiveStarships)
        every { ActiveStarships.findContaining(any()) } returns null

        SpaceHazards.applyStarHazards(player)

        assertAffected()
    }

    @Test
    fun applyStarHazardsDoesNotAffectDeadPlayers() {
        mockkObject(StarCache)
        every { StarCache.getAll() } returns listOf(mockk<Star>().also { star ->
            every { star.classification } answers { StarClassification.MAIN_SEQUENCE_G }
            every { star.instance } answers {
                mockk<StarInstance>().also { instance ->
                    every { instance.spaceWorld } answers { player.world }
                    every { instance.position } answers { Vec3i(player.location) }
                }
            }
        })

        mockkObject(ActiveStarships)
        every { ActiveStarships.findContaining(any()) } returns null

        SpaceHazards.applyStarHazards(player)
        assertAffected()

        player.fireTicks = 0
        player.health = 0.0
        SpaceHazards.applyStarHazards(player)
        assertNotAffected()
    }

    @Test
    fun applyStarHazardsDoesNotAffectPlayersNotNearStars() {
        mockkObject(StarCache)
        every { StarCache.getAll() } returns LinkedList()

        mockkObject(ActiveStarships)
        every { ActiveStarships.findContaining(any()) } returns null

        SpaceHazards.applyStarHazards(player)
        assertNotAffected()
    }

    @Test
    fun applyStarHazardsDoesNotAffectPlayersRidingShips() {
        mockkObject(StarCache)
        every { StarCache.getAll() } returns listOf(mockk<Star>().also { star ->
            every { star.classification } answers { StarClassification.MAIN_SEQUENCE_G }
            every { star.instance } answers {
                mockk<StarInstance>().also { instance ->
                    every { instance.spaceWorld } answers { player.world }
                    every { instance.position } answers { Vec3i(player.location) }
                }
            }
        })

        mockkObject(ActiveStarships)
        every { ActiveStarships.findAllContaining(any()) } returns listOf(mockk<ActivePlayerStarship>().also { starship ->
            every { starship.shields } answers {
                LinkedList(listOf(mockk<SphereShieldSubsystem>().also {
                    every { it.containsBlock(any()) } returns true
                    every { it.powerRatio } answers { 1.0 }
                }))
            }
        })

        SpaceHazards.applyStarHazards(player)

        assertNotAffected()
    }

    private fun assertAffected() {
        Assertions.assertTrue(player.fireTicks > 0) { "player must be affected" }
    }

    private fun assertNotAffected() {
        Assertions.assertFalse(player.fireTicks > 0) { "player must not be affected" }
    }
}
