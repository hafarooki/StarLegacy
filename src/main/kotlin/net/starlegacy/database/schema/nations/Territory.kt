package net.starlegacy.database.schema.nations

import com.mongodb.client.result.UpdateResult
import net.starlegacy.database.DbObject
import net.starlegacy.database.Oid
import net.starlegacy.database.OidDbObjectCompanion
import net.starlegacy.database.generateOid
import org.litote.kmongo.*
import java.time.DayOfWeek
import java.util.Date

data class Territory(
    override val _id: Oid<Territory>,
    /** The name of the capture */
    var name: String,
    /** The world the capture is in */
    var worldName: String,
    /** The X-coordinate of the center of the capture */
    var x: Int,
    /** The Z-coordinate of the center of the capture */
    var z: Int,
    /** The radius of the capture's land */
    var radius: Int,
    /** The schedule for where the capture's bastions can be sieged */
    var siegeSchedule: SiegeTime,
    /** The nation that currently owns the capture */
    var nationId: Oid<Nation>? = null,
    /** The last time the owning nation rescheduled the siege timing */
    var rescheduleTimestamp: Date? = null,
    /** The last time someone besides the owning nation tried to capture any bastion */
    var attackTimestamp: Date? = null,
    /** The last time the owning nation tried to recapture any bastion */
    var recaptureTimestamp: Date? = null,
    /** The station's capturable bastions */
    var bastions: MutableList<Bastion> = mutableListOf(),
) : DbObject {
    enum class SiegePeriod(val text: String) {
        PERIOD_1("12:00 until 15:00"),
        PERIOD_2("15:00 until 18:00"),
        PERIOD_3("18:00 until 21:00"),
    }

    data class Bastion(
        var name: String,
        var x: Int,
        var y: Int,
        var z: Int,
        var occupierId: Oid<Nation>?
    )

    data class SiegeTime(
        val dayOfWeek: DayOfWeek,
        val period: SiegePeriod
    )

    companion object : OidDbObjectCompanion<Territory>(Territory::class, setup = {
        ensureUniqueIndex(Territory::name)
        ensureIndex(Territory::nationId)
        ensureUniqueIndex(Territory::worldName, Territory::x, Territory::z)
    }) {
        fun create(
            name: String,
            world: String,
            x: Int,
            z: Int,
            radius: Int,
            siegeTime: SiegeTime
        ): Oid<Territory> {
            val id: Oid<Territory> = generateOid()
            col.insertOne(Territory(id, name, world, x, z, radius, siegeTime))
            return id
        }

        fun setNation(territoryId: Oid<Territory>, nationId: Oid<Nation>): UpdateResult {
            return col.updateOneById(territoryId, setValue(Territory::nationId, nationId))
        }

        fun save(territory: Territory) {
            col.updateOne(territory)
        }

        fun delete(territory: Territory) {
            col.deleteOneById(territory._id)
        }
    }
}
