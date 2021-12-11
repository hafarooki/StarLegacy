package net.starlegacy.database.schema.economy

import com.mongodb.client.FindIterable
import com.mongodb.client.model.Filters
import net.starlegacy.database.*
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.schema.nations.Settlement
import net.starlegacy.feature.starship.factory.StarshipFactories
import org.bson.conversions.Bson
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.litote.kmongo.*

data class PlayerMerchant(
    override val _id: Oid<PlayerMerchant> = generateOid(),
    var name: String,
    var playerId: SLPlayerId,
    var settlementId: Oid<Settlement>,
    var x: Double,
    var y: Double,
    var z: Double,
    var skinData: String,
    var balance: Int,
    val importListings: MutableList<ImportListing> = mutableListOf(),
    val exportListings: MutableList<ExportListing> = mutableListOf(),
) : DbObject {
    data class ImportListing(
        var itemString: String,
        var desiredAmount: Int,
        var pricePerItem: Int,
        var stock: Int
    )

    data class ExportListing(
        var itemString: String,
        var stock: Int,
        var pricePerItem: Int,
    )

    companion object : OidDbObjectCompanion<PlayerMerchant>(PlayerMerchant::class, setup = {
        ensureUniqueIndexCaseInsensitive(PlayerMerchant::name, PlayerMerchant::playerId)
        ensureIndex(PlayerMerchant::playerId)
        ensureIndex(PlayerMerchant::settlementId)
    }) {
        fun create(
            playerId: SLPlayerId,
            settlementId: Oid<Settlement>,
            name: String,
            x: Double,
            y: Double,
            z: Double,
            skinData: String,
            balance: Int
        ): Oid<PlayerMerchant> {
            val id = generateOid<PlayerMerchant>()
            col.insertOne(PlayerMerchant(id, name, playerId, settlementId, x, y, z, skinData, balance))
            return id
        }

        fun delete(id: Oid<PlayerMerchant>) {
            col.deleteOneById(id)
        }

        fun deleteAt(territory: Oid<Settlement>) {
            col.deleteMany(PlayerMerchant::settlementId eq territory)
        }

        fun findAt(territory: Oid<Settlement>): FindIterable<PlayerMerchant> = col.find(PlayerMerchant::settlementId eq territory)

        fun nameQuery(name: String): Bson = Filters.regex("name", "^$name$", "i")

        fun findByName(name: String, slPlayerId: SLPlayerId): PlayerMerchant? {
            return findOne(and(nameQuery(name), PlayerMerchant::playerId eq slPlayerId))
        }

        fun findAllByPlayer(slPlayerId: SLPlayerId): List<PlayerMerchant> {
            return col.find(PlayerMerchant::playerId eq slPlayerId).toList()
        }

        fun save(merchant: PlayerMerchant) {
            col.updateOne(merchant)
        }

        fun delete(merchant: PlayerMerchant) {
            col.deleteOneById(merchant._id)
        }
    }
}
