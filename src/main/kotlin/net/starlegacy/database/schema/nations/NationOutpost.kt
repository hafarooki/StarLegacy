package net.starlegacy.database.schema.nations

import com.mongodb.client.model.Filters
import net.starlegacy.database.DbObject
import net.starlegacy.database.Oid
import net.starlegacy.database.OidDbObjectCompanion
import net.starlegacy.database.generateOid
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.trx
import org.litote.kmongo.*

data class NationOutpost(
    override val _id: Oid<NationOutpost>,
    var nationId: Oid<Nation>,
    var name: String,
    var worldName: String,
    var centerX: Int,
    var centerZ: Int,
    var radius: Int,
    var managerIds: Set<SLPlayerId>,
    var trustedPlayerIds: Set<SLPlayerId>,
    var trustedNationIds: Set<Oid<Nation>>,
    var trustLevel: TrustLevel
) : DbObject {
    enum class TrustLevel { MANUAL, NATION, ALLY }

    companion object : OidDbObjectCompanion<NationOutpost>(NationOutpost::class, setup = {
        ensureUniqueIndex(NationOutpost::name)
        ensureIndex(NationOutpost::nationId)
        ensureIndex(NationOutpost::managerIds)
        ensureIndex(NationOutpost::trustedPlayerIds)
        ensureIndex(NationOutpost::trustedNationIds)
    }) {
        private fun nameQuery(name: String) = Filters.regex("name", "^$name$", "i")

        fun create(
            nation: Oid<Nation>, name: String, world: String, x: Int, z: Int, radius: Int
        ): Oid<NationOutpost> = trx { sess ->
            check(NationOutpost.none(sess, nameQuery(name)))

            val id = generateOid<NationOutpost>()
            val trustLevel = TrustLevel.MANUAL
            val station = NationOutpost(id, nation, name, world, x, z, radius, setOf(), setOf(), setOf(), trustLevel)
            col.insertOne(sess, station)
            return@trx id
        }

        fun setRadius(outpostId: Oid<NationOutpost>, newRadius: Int) {
            col.updateOneById(outpostId, NationOutpost::radius setTo newRadius)
        }

        fun delete(id: Oid<NationOutpost>) {
            col.deleteOneById(id)
        }
    }
}
