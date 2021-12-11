package net.starlegacy

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import net.starlegacy.util.redisaction.RedisAction
import net.starlegacy.util.redisaction.RedisActions
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

abstract class SLComponent : Listener {
    protected val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(javaClass)

    protected val plugin get() = PLUGIN

    init {
        if (StarLegacy.INITIALIZATION_COMPLETE) {
            error("Initialized ${this.javaClass.simpleName} after plugin initialization!")
        }
    }

    open fun onEnable() {}

    open fun onDisable() {}

    protected inline fun <reified T : Event> subscribe(
        priority: EventPriority = EventPriority.NORMAL,
        ignoreCancelled: Boolean = false,
        noinline block: (T) -> Unit
    ): Unit = plugin.listen(priority, ignoreCancelled, block)

    protected inline fun <reified T : Event> subscribe(
        priority: EventPriority = EventPriority.NORMAL,
        ignoreCancelled: Boolean = false,
        noinline block: (Listener, T) -> Unit
    ): Unit = plugin.listen(priority, ignoreCancelled, block)

    private fun addPacketListener(
        priority: ListenerPriority,
        packetType: PacketType,
        send: Boolean,
        listener: (PacketEvent) -> Unit
    ) {
        val packetAdapter = object : PacketAdapter(PLUGIN, priority, packetType) {
            override fun onPacketReceiving(event: PacketEvent) {
                if (!send) {
                    listener(event)
                }
            }

            override fun onPacketSending(event: PacketEvent) {
                if (send) {
                    listener(event)
                }
            }
        }

        ProtocolLibrary.getProtocolManager().addPacketListener(packetAdapter)
    }

    protected fun addPacketSendListener(
        priority: ListenerPriority,
        packetType: PacketType,
        listener: (PacketEvent) -> Unit
    ) = this.addPacketListener(priority, packetType, true, listener)

    protected fun addPacketReceiveListener(
        priority: ListenerPriority,
        packetType: PacketType,
        listener: (PacketEvent) -> Unit
    ) = this.addPacketListener(priority, packetType, false, listener)

    inline fun <reified T, B> ((T) -> B).registerRedisAction(id: String, runSync: Boolean = true): RedisAction<T> {
        return RedisActions.register(id, runSync, this)
    }

    open fun supportsVanilla(): Boolean = false
}
