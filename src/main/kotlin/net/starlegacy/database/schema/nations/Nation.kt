package net.starlegacy.database.schema.nations

import com.mongodb.client.MongoIterable
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import net.starlegacy.database.DbObject
import net.starlegacy.database.Oid
import net.starlegacy.database.OidDbObjectCompanion
import net.starlegacy.database.ensureUniqueIndexCaseInsensitive
import net.starlegacy.database.generateOid
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.schema.starships.Blueprint
import net.starlegacy.database.trx
import org.bukkit.Color
import org.litote.kmongo.*
import org.litote.kmongo.util.KMongoUtil.idFilterQuery

/**
 * Referenced on:
 * - Territory (for territory owner)
 * - NationRole (for parent)
 * - CapturableStation (for owner)
 * - NationRelation (for both the nation in question, and the other nation)
 * - Settlement (for the nation it's in)
 * - SLPlayer (for the nation it's currently in)
 * - CapturableStationSiege (for who sieged it)
 * - SettlementZone (trusted nations)
 * - SpaceStation (owning nation)
 * - SpaceStation (trusted nations)
 * - Blueprint (trusted nations)
 *
 * @property name The name of the nation (user-adjustable)
 * @property capitalId The capital of the settlement. Also determines the leader.
 * @property color The color of the nation (for map and blasters etc)
 * @property balance The amount of money the nation has
 * @property invites The settlements the nation has invited
 */
data class Nation(
    override val _id: Oid<Nation> = generateOid(),
    val name: String,
    val capitalId: Oid<Settlement>,
    val color: Int,
    override val balance: Int = 0,
    val invites: Set<Oid<Settlement>> = setOf()
) : DbObject, MoneyHolder {
    companion object : OidDbObjectCompanion<Nation>(Nation::class, setup = {
        ensureUniqueIndexCaseInsensitive(Nation::name, indexOptions = IndexOptions().textVersion(3))
        ensureUniqueIndex(Nation::capitalId)
        ensureIndex(Nation::invites)
    }) {
        private fun nameQuery(name: String) = Filters.regex("name", "^$name$", "i")

        fun findByName(name: String): Oid<Nation>? = findOneProp(nameQuery(name), Nation::_id)

        fun create(name: String, capitalId: Oid<Settlement>, color: Int): Oid<Nation> = trx { sess ->
            check(none(sess, nameQuery(name)))

            // check the settlement isn't already in a nation. will also fail if there's no such settlement
            check(Settlement.matches(sess, capitalId, Settlement::nationId eq null))

            val id: Oid<Nation> = generateOid()

            // update the settlements members
            SLPlayer.col.updateMany(
                sess, SLPlayer::settlementId eq capitalId, setValue(
                    SLPlayer::nationId, id
                )
            )

            // update the settlement
            Settlement.updateById(sess, capitalId, setValue(Settlement::nationId, id))

            // create the actual nation
            col.insertOne(sess, Nation(id, name, capitalId, color))

            return@trx id
        }

        fun delete(id: Oid<Nation>): Unit = trx { sess ->
            require(exists(sess, id))

            // Update all the stations owned by the nation
            Territory.col.updateMany(
                sess,
                Territory::nationId eq id,
                setValue(Territory::bastions / Territory.Bastion::occupierId, null)
            )
            Territory.col.updateMany(
                sess,
                Territory::nationId eq id,
                setValue(Territory::nationId, null)
            )

            // remove from zones it's trusted to
            SettlementZone.col.updateMany(
                sess,
                SettlementZone::trustedNations ne null,
                pull(SettlementZone::trustedNations, id)
            )

            // unset the nation of all member settlements
            Settlement.col.updateMany(
                sess, Settlement::nationId eq id,
                setValue(Settlement::nationId, null)
            )

            // Delete all the nation roles associated with the nation
            NationRole.col.deleteMany(sess, NationRole::parent eq id)

            // Delete all the nation relations associated with the nation
            NationRelation.col.deleteMany(
                sess, or(NationRelation::nationId eq id, NationRelation::otherId eq id)
            )

            // unset nation for all members
            SLPlayer.col.updateMany(
                sess, SLPlayer::nationId eq id, setValue(SLPlayer::nationId, null)
            )

            NationOutpost.col.updateMany(
                sess, NationOutpost::nationId ne id, pull(NationOutpost::trustedNationIds, id)
            )

            NationOutpost.col.deleteMany(sess, NationOutpost::nationId eq id)

            Blueprint.col.updateMany(
                sess,
                Blueprint::trustedNationIds contains id,
                pull(Blueprint::trustedNationIds, id)
            )

            // Remove the nation itself
            col.deleteOne(sess, idFilterQuery(id))
        }

        fun deposit(nationId: Oid<Nation>, amount: Int) {
            check(amount >= 0)

            updateById(nationId, inc(Nation::balance, amount))
        }

        fun withdraw(nationId: Oid<Nation>, amount: Int) {
            check(amount >= 0)

            updateById(nationId, inc(Nation::balance, -amount))
        }

        fun getSettlements(nationId: Oid<Nation>): MongoIterable<Oid<Settlement>> {
            return Settlement.findProp(Settlement::nationId eq nationId, Settlement::_id)
        }

        fun getPlayers(nationId: Oid<Nation>): MongoIterable<SLPlayerId> {
            return SLPlayer.findProp(SLPlayer::nationId eq nationId, SLPlayer::_id)
        }

        fun isInvited(nationId: Oid<Nation>, settlementId: Oid<Settlement>): Boolean {
            return matches(nationId, Nation::invites contains settlementId)
        }

        fun addInvite(nationId: Oid<Nation>, settlementId: Oid<Settlement>) {
            updateById(nationId, addToSet(Nation::invites, settlementId))
        }

        fun removeInvite(nationId: Oid<Nation>, settlementId: Oid<Settlement>) {
            updateById(nationId, pull(Nation::invites, settlementId))
        }

        fun getMembers(nationId: Oid<Nation>): MongoIterable<SLPlayerId> = SLPlayer
            .findProp(SLPlayer::nationId eq nationId, SLPlayer::_id)

        fun setName(nationId: Oid<Nation>, newName: String): Unit = trx { sess ->
            require(none(sess, and(Nation::_id ne nationId, nameQuery(newName))))
            { "A different nation with that name already exists" }

            updateById(sess, nationId, setValue(Nation::name, newName))
        }

        fun setColor(nationId: Oid<Nation>, rgb: Int) {
            Color.fromRGB(rgb) // this will throw an exception if it's invalid

            updateById(nationId, setValue(Nation::color, rgb))
        }

        fun setCapital(nationId: Oid<Nation>, settlementId: Oid<Settlement>): Unit = trx { sess ->
            require(Settlement.matches(sess, settlementId, Settlement::nationId eq nationId))
            { "Settlement not in nation" }

            require(matches(sess, nationId, Nation::capitalId ne settlementId))
            { "Settlement is already the capital" }

            updateById(sess, nationId, setValue(Nation::capitalId, settlementId))
        }
    }

    data class Relation(val wish: NationRelation, val actual: NationRelation)
}
