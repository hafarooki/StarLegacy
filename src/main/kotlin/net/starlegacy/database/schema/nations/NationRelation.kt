package net.starlegacy.database.schema.nations

import com.mongodb.client.ClientSession
import net.starlegacy.database.DbObject
import net.starlegacy.database.Oid
import net.starlegacy.database.OidDbObjectCompanion
import net.starlegacy.database.generateOid
import net.starlegacy.database.trx
import net.starlegacy.util.SLTextStyle
import org.litote.kmongo.and
import org.litote.kmongo.combine
import org.litote.kmongo.ensureIndex
import org.litote.kmongo.ensureUniqueIndex
import org.litote.kmongo.eq
import org.litote.kmongo.setOnInsert
import org.litote.kmongo.upsert

/**
 * Referenced on: None
 */
data class NationRelation(
    override val _id: Oid<NationRelation> = generateOid(),
    val nationId: Oid<Nation>,
    val otherId: Oid<Nation>,
    var wish: Level,
    var actual: Level
) : DbObject {
    companion object : OidDbObjectCompanion<NationRelation>(NationRelation::class, setup = {
        ensureUniqueIndex(NationRelation::nationId, NationRelation::otherId)
        ensureIndex(NationRelation::nationId)
        ensureIndex(NationRelation::otherId)
    }) {
        fun getRelationWish(nationId: Oid<Nation>, otherId: Oid<Nation>): Level = when (nationId) {
            otherId -> Level.NATION
            else -> findOneProp(
                and(NationRelation::nationId eq nationId, NationRelation::otherId eq otherId), NationRelation::wish
            ) ?: Level.NONE
        }

        fun changeRelationWish(nationId: Oid<Nation>, otherId: Oid<Nation>, wish: Level): Level = trx { sess ->
            val otherWish: Level = getRelationWish(otherId, nationId)
            val actual: Level = wish.lowest(otherWish)
            setRelation(sess, nationId, otherId, wish, actual)
            setRelation(sess, otherId, nationId, otherWish, actual)
            return@trx actual
        }

        private fun setRelation(
            sess: ClientSession,
            nationId: Oid<Nation>,
            otherId: Oid<Nation>,
            wish: Level,
            actual: Level
        ) = col.updateOne(
            sess,
            and(NationRelation::nationId eq nationId, NationRelation::otherId eq otherId),
            combine(
                setOnInsert(NationRelation::nationId, nationId),
                setOnInsert(NationRelation::otherId, otherId),
                org.litote.kmongo.setValue(NationRelation::wish, wish),
                org.litote.kmongo.setValue(NationRelation::actual, actual)
            ),
            upsert()
        )
    }

    /** Relation wishes nations can set to other nations */
    enum class Level(val textStyle: SLTextStyle) {
        ENEMY(SLTextStyle.RED),
        NONE(SLTextStyle.GRAY),
        NEUTRAL(SLTextStyle.LIGHT_PURPLE),
        ALLY(SLTextStyle.DARK_PURPLE),
        NATION(SLTextStyle.GREEN);

        fun lowest(other: Level): Level = when {
            other.ordinal > this.ordinal -> this
            else -> other
        }

        val coloredName = "$textStyle$name"
    }
}
