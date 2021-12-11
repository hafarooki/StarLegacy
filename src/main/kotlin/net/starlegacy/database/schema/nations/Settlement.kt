package net.starlegacy.database.schema.nations

import com.mongodb.client.ClientSession
import com.mongodb.client.MongoIterable
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import net.starlegacy.database.*
import net.starlegacy.database.schema.economy.PlayerMerchant
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.util.*
import org.bson.conversions.Bson
import org.bukkit.block.BlockFace
import org.litote.kmongo.*
import org.litote.kmongo.util.KMongoUtil.idFilterQuery
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.geom.Area
import java.util.concurrent.LinkedBlockingQueue
import kotlin.collections.ArrayList


/**
 * Referenced on:
 * - Nation (for capital)
 * - Nation (for settlements they invited)
 * - SettlementRole (for parent)
 * - SLPlayer.NationData (for what they're a member of)
 * - SettlementZone (for parent)
 * - SettlementZone (for trusted)
 */
data class Settlement(
    override val _id: Oid<Settlement>,
    /** The name of the settlement (user-adjustable) */
    val name: String,
    /** The world that the settlement is in */
    val worldName: String,
    /** The chunks of the settlement's land */
    val chunks: Set<ChunkKey>,
    /** The leader of the settlement */
    val leaderId: SLPlayerId,
    /** The amount of money the settlement has */
    override val balance: Int = 0,
    /** The nation the settlement is in */
    val nationId: Oid<Nation>? = null,
    /** The minimum foreign relation a player must have to build in the settlement */
    val minimumBuildAccess: ForeignRelation = ForeignRelation.SETTLEMENT_MEMBER,
    /** List of players the settlement has invited */
    val invites: Set<SLPlayerId> = setOf(),
) : DbObject, MoneyHolder {
    companion object : OidDbObjectCompanion<Settlement>(Settlement::class, setup = {
        ensureUniqueIndex(Settlement::chunks)
        ensureUniqueIndexCaseInsensitive(Settlement::name, indexOptions = IndexOptions().textVersion(3))
        ensureUniqueIndex(Settlement::leaderId)
        ensureIndex(Settlement::nationId)
        ensureIndex(Settlement::invites)
    }) {
        fun nameQuery(name: String): Bson = Filters.regex("name", "^$name$", "i")

        fun findByName(name: String): Oid<Settlement>? {
            return findOneProp(nameQuery(name), Settlement::_id)
        }

        fun getName(settlementId: Oid<Settlement>): String? = findPropById(settlementId, Settlement::name)

        fun getNation(settlementId: Oid<Settlement>): Oid<Nation>? = findPropById(settlementId, Settlement::nationId)

        fun getMembers(settlementId: Oid<Settlement>): MongoIterable<SLPlayerId> = SLPlayer
            .findProp(SLPlayer::settlementId eq settlementId, SLPlayer::_id)

        fun isCapital(settlementId: Oid<Settlement>?): Boolean = !Nation.none(Nation::capitalId eq settlementId)

        fun isInvitedTo(settlementId: Oid<Settlement>, slPlayer: SLPlayerId): Boolean =
            matches(settlementId, Settlement::invites contains slPlayer)

        fun addInvite(settlementId: Oid<Settlement>, slPlayer: SLPlayerId): Unit = trx { sess ->
            require(!SLPlayer.matches(sess, slPlayer, SLPlayer::settlementId eq settlementId))
            require(!matches(sess, settlementId, Settlement::invites contains slPlayer))
            updateById(sess, settlementId, addToSet(Settlement::invites, slPlayer))
        }

        fun removeInvite(settlementId: Oid<Settlement>, slPlayer: SLPlayerId): Unit = trx { sess ->
            require(matches(sess, settlementId, Settlement::invites contains slPlayer))
            updateById(sess, settlementId, pull(Settlement::invites, slPlayer))
        }

        private fun updateMembers(session: ClientSession, settlementId: Oid<Settlement>, vararg update: Bson): Long {
            return SLPlayer.col.updateMany(session, SLPlayer::settlementId eq settlementId, combine(*update))
                .matchedCount
        }

        fun create(world: String, chunks: Set<ChunkKey>, name: String, leader: SLPlayerId): Oid<Settlement> =
            trx { sess ->
                require(none(sess, nameQuery(name)))
                require(SLPlayer.matches(sess, leader, SLPlayer::settlementId eq null))

                val id: Oid<Settlement> = generateOid()
                val settlement = Settlement(id, name, world, chunks, leader)

                SLPlayer.col.updateOne(
                    sess,
                    idFilterQuery(leader),
                    setValue(SLPlayer::settlementId, id)
                )
                col.insertOne(sess, settlement)

                return@trx id
            }

        fun claim(settlementId: Oid<Settlement>, chunks: Set<ChunkKey>) {
            updateById(settlementId, addEachToSet(Settlement::chunks, chunks.toList()))
        }

        fun unclaim(settlementId: Oid<Settlement>, chunks: Set<ChunkKey>) {
            updateById(settlementId, pullAll(Settlement::chunks, chunks.toList()))
        }

        fun delete(settlementId: Oid<Settlement>) {
            // leave nation first, to update members that they are no longer in a nation, remove nation roles, etc
            leaveNation(settlementId)

            trx { sess ->
                require(exists(sess, settlementId))

                // make the members no long members
                updateMembers(sess, settlementId, set(SLPlayer::settlementId setTo null, SLPlayer::nationId setTo null))

                // remove all related settlement roles
                SettlementRole.col.deleteMany(sess, SettlementRole::parent eq settlementId)

                // remove/update all the relevant settlement regions
                SettlementZone.col.deleteMany(sess, SettlementZone::settlementId eq settlementId)
                SettlementZone.col.updateMany(
                    sess,
                    SettlementZone::trustedSettlements ne null,
                    pull(SettlementZone::trustedSettlements, settlementId)
                )

                // remove invite from nations
                Nation.col.updateAll(sess, pull(Nation::invites, settlementId))

                // remove all contained merchants
                PlayerMerchant.col.deleteMany(sess, PlayerMerchant::settlementId eq settlementId)

                // remove the actual settlement
                col.deleteOne(sess, idFilterQuery(settlementId))
            }
        }

        fun leaveNation(settlementId: Oid<Settlement>): Boolean = trx { sess ->
            require(exists(sess, settlementId))
            require(Nation.none(sess, Nation::capitalId eq settlementId))

            val members: List<SLPlayerId> = getMembers(settlementId).toList()

            // remove roles
            NationRole.col.updateAll(sess, pullAll(NationRole::members, members))

            // unset nation for members
            updateMembers(sess, settlementId, setValue(SLPlayer::nationId, null))

            // unset actual settlement nation
            return@trx col.updateOne(
                sess, idFilterQuery(settlementId),
                setValue(Settlement::nationId, null)
            ).modifiedCount > 0
        }

        fun joinNation(settlementId: Oid<Settlement>, nationId: Oid<Nation>): Unit = trx { sess ->
            require(exists(sess, settlementId))

            // require the settlement isn't already in a nation
            require(matches(sess, settlementId, Settlement::nationId eq null))

            // update the nation of all members
            updateMembers(sess, settlementId, setValue(SLPlayer::nationId, nationId))

            // set the nation to the new nation
            col.updateOne(sess, idFilterQuery(settlementId), setValue(Settlement::nationId, nationId))
        }

        fun deposit(settlementId: Oid<Settlement>, amount: Int) {
            updateById(settlementId, inc(Settlement::balance, amount))
        }

        fun withdraw(settlementId: Oid<Settlement>, amount: Int) {
            updateById(settlementId, inc(Settlement::balance, -amount))
        }

        fun setName(settlementId: Oid<Settlement>, name: String): Unit = trx { sess ->
            require(none(sess, and(Settlement::_id ne settlementId, nameQuery(name))))
            updateById(sess, settlementId, setValue(Settlement::name, name))
        }

        fun setLeader(settlementId: Oid<Settlement>, slPlayerId: SLPlayerId): Unit = trx { sess ->
            require(SLPlayer.matches(sess, slPlayerId, SLPlayer::settlementId eq settlementId))
            updateById(sess, settlementId, setValue(Settlement::leaderId, slPlayerId))
        }

        fun setMinBuildAccess(settlementId: Oid<Settlement>, level: ForeignRelation) {
            updateById(settlementId, setValue(Settlement::minimumBuildAccess, level))
        }
    }

    enum class ForeignRelation { NONE, ALLY, NATION_MEMBER, SETTLEMENT_MEMBER, STRICT; }
}
