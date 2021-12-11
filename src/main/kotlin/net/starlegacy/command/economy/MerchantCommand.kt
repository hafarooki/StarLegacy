package net.starlegacy.command.economy

import co.aikar.commands.ConditionFailedException
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.Subcommand
import net.starlegacy.cache.nations.SettlementCache
import net.starlegacy.command.SLCommand
import net.starlegacy.database.schema.economy.PlayerMerchant
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.nations.Settlement
import net.starlegacy.database.slPlayerId
import net.starlegacy.database.uuid
import net.starlegacy.feature.economy.merchant.PlayerMerchants
import net.starlegacy.feature.economy.merchant.menus.merchantBrowse
import net.starlegacy.feature.nations.region.Regions
import net.starlegacy.feature.nations.region.types.RegionSettlement
import net.starlegacy.feature.starship.factory.StarshipFactories
import net.starlegacy.util.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.math.roundToInt

@CommandAlias("merchant")
object MerchantCommand : SLCommand() {
    private fun getCurrentSettlementContext(sender: Player): Pair<Location, Settlement> {
        val location: Location = sender.location
        val region: RegionSettlement = Regions.findFirstOf(location)
            ?: fail { "You aren't in a settlement" }
        val settlement: Settlement = SettlementCache[region.dbObjectId]
        return Pair(location, settlement)
    }

    private fun requireCanManage(sender: Player) {
        for (region in Regions.find(sender.location)) {
            if (!region.canAccess(sender)) {
                throw ConditionFailedException("You don't have build access in this settlement!")
            }
        }
    }

    @Subcommand("create")
    fun onCreate(sender: Player, name: String, skin: String) = asyncCommand(sender) {
        val (location, settlementId) = getCurrentSettlementContext(sender)

        validateMerchantName(name, sender)

        requireCanManage(sender)

        val balance = 0
        val id: UUID = getIdFromName(skin)
        val skinData: Skins.SkinData = Skins[id] ?: fail { "Failed to retrieve skin for $skin!" }

        val serializedSkinData = Base64.getEncoder().encodeToString(skinData.toBytes())
        PlayerMerchant.create(
            sender.slPlayerId,
            settlementId._id,
            name,
            location.toBlockLocation().x,
            location.toBlockLocation().y,
            location.toBlockLocation().z,
            serializedSkinData,
            balance,
        )
        PlayerMerchants.synchronizeAsync()

        sender msg green("Created NPC!")
    }

    private fun validateMerchantName(name: String, sender: Player) {
        failIf(PlayerMerchant.findByName(name, sender.slPlayerId) != null) {
            "You already have a merchant named $name"
        }

        failIf(name != name.toLowerCase()) {
            "Name must be lowercase"
        }

        failIf(name.length !in 3..20) {
            "Name must be between 3 and 20 characters"
        }
    }

    private fun getIdFromName(name: String): UUID = SLPlayer[name]?._id?.uuid
        ?: Bukkit.createProfile(name).apply { complete(false) }.id
        ?: fail { "Player $name not found" }

    @Subcommand("delete|remove")
    fun onDelete(sender: Player, merchantName: String) = asyncCommand(sender) {
        val merchant = retrieveMerchantByName(sender, merchantName)
        failIf(merchant.importListings.isNotEmpty()) { "Can't delete merchant with import listings, remove import listings first." }
        failIf(merchant.exportListings.isNotEmpty()) { "Can't delete merchant with export listings, remove export listings first." }

        if (merchant.balance > 0) {
            VAULT_ECO.depositPlayer(sender, merchant.balance.toDouble())
            sender msg green("Refunded ${merchant.balance.toCreditsString()} from merchant's balance")
        }

        PlayerMerchant.delete(merchant._id)
        PlayerMerchants.synchronizeAsync() // update the actual npc

        sender msg green("Deleted NPC!")
    }

    @Subcommand("list")
    fun onList(sender: Player) {
        sender msg green("Merchants:")

        for (merchant in PlayerMerchant.findAllByPlayer(sender.slPlayerId)) {
            sender msg ("&2${merchant.name}&b, Coordinates: (&a${merchant.x.toInt()}&b, &a${merchant.y.toInt()}&b, &a${merchant.z.toInt()}&b)&b, Balance: &a${merchant.balance}".colorize())
        }
    }

    @Subcommand("balance|bal")
    fun onBalance(sender: Player, merchantName: String) {
        val merchant = retrieveMerchantByName(sender, merchantName)

        sender msg green("${merchant.name}'s balance: C${merchant.balance}")
    }

    @Subcommand("deposit")
    fun onDeposit(sender: Player, amount: Int, merchantName: String) = asyncCommand(sender) {
        val merchant = retrieveMerchantByName(sender, merchantName)

        failIf(amount <= 0) { "Amount must be greater than 0" }

        val region: RegionSettlement? = Regions.findFirstOf(sender.location)

        val feePercent = if (region == null || merchant.settlementId != region.dbObjectId) 0.05 else 0.0

        failIf(!VAULT_ECO.has(sender, amount.toDouble()))
        {
            "You don't have ${amount.toCreditsString()}! You only have ${
                VAULT_ECO.getBalance(sender).toCreditsString()
            }"
        }

        val feeAmount = (amount * feePercent).roundToInt()
        val depositedAmount = amount - feeAmount

        deposit(merchant, depositedAmount)
        VAULT_ECO.withdrawPlayer(sender, amount.toDouble())

        sender msg "&aDeposited ${depositedAmount.toCreditsString()}" +
                " (${feeAmount.toCreditsString()} fee)"
    }

    @Subcommand("withdraw")
    fun onWithdraw(sender: Player, amount: Int, merchantName: String) {
        val merchant = retrieveMerchantByName(sender, merchantName)

        failIf(amount <= 0) { "Amount must be greater than 0" }

        val balance = sender.getMoneyBalance()
        failIf(balance < amount)
        { "There's not enough money to withdraw ${amount.toCreditsString()}. Balance is ${balance.toCreditsString()}" }

        val region: RegionSettlement? = Regions.findFirstOf(sender.location)

        val feePercent = if (region == null || merchant.settlementId != region.dbObjectId) 0.05 else 0.0

        val feeAmount = (amount * feePercent).roundToInt()
        val withdrawnAmount = amount - feeAmount

        withdraw(merchant, amount)
        VAULT_ECO.depositPlayer(sender, withdrawnAmount.toDouble())

        sender msg "&aWithdrew ${withdrawnAmount.toCreditsString()} (${feeAmount.toCreditsString()} fee)"
    }

    @Subcommand("collect")
    fun onCollect(sender: Player, merchantId: String, itemName: String, amount: Int) {
        val item: ItemStack

        try {
            item = StarshipFactories.fromItemString(itemName)
        } catch (e: Exception) {
            fail { e.message ?: "Invalid item string" }
        }
        val merchant = retrieveMerchantByName(sender, merchantId)

        item.amount = amount

        for (importListing in merchant.importListings) {
            if (importListing.itemString == item.type.toString()) {
                if (importListing.stock < amount) {
                    item.amount = importListing.stock
                }
                importListing.stock -= amount

                if (importListing.desiredAmount < 1 && importListing.stock < 1) {
                    merchant.importListings.remove(importListing)
                }

                PlayerMerchant.save(merchant)

                sender.inventory.addItem(item)

                sender msg green("Collected ${item.amount} ${item.type}(s)")
                return
            }
        }
        fail { "No $itemName(s) available to collect" }
    }

    @Subcommand("browse")
    fun onBrowse(sender: Player) {
        merchantBrowse(sender)
    }

    @Subcommand("import add")
    fun onImportCreate(sender: Player, merchantId: String, itemName: String, amount: Int, pricePerItem: Int) {
        val item: ItemStack

        try {
            item = StarshipFactories.fromItemString(itemName)
        } catch (e: Exception) {
            fail { e.message ?: "Invalid item string" }
        }

        item.amount = amount

        val merchant = retrieveMerchantByName(sender, merchantId)

        val creditsNeeded = pricePerItem * amount

        failIf(merchant.balance < creditsNeeded) {
            "$merchantId doesn't have enough credits!"
        }

        for (importListing in merchant.importListings) {
            if (importListing.itemString == itemName) {
                importListing.desiredAmount += amount

                sender msg green("Charged C${importListing.pricePerItem * amount} from merchant $merchantId")

                merchant.balance -= importListing.pricePerItem * amount

                PlayerMerchant.save(merchant)

                sender msg green("Added $amount $itemName(s) to an existing import listing")

                return
            }
        }

        val importListing = PlayerMerchant.ImportListing(itemName, amount, pricePerItem, 0)

        sender msg green("Charged C$creditsNeeded from merchant $merchantId")

        merchant.balance -= pricePerItem * amount

        merchant.importListings.add(importListing)

        PlayerMerchant.save(merchant)

        sender msg green("Created import listing of $amount $itemName(s) with a price per item of $pricePerItem")
    }


    @Subcommand("import remove")
    fun onImportRemove(sender: Player, merchantId: String, itemName: String) {
        val item: ItemStack

        try {
            item = StarshipFactories.fromItemString(itemName)
        } catch (e: Exception) {
            fail { e.message ?: "Invalid item string" }
        }

        val itemString = StarshipFactories.toItemString(item)

        val merchant = retrieveMerchantByName(sender, merchantId)

        for (importListing in merchant.importListings) {
            if (importListing.itemString != itemString) {
                continue
            }

            failIf(importListing.stock > 0) { "Collect items first" }
            merchant.balance += importListing.desiredAmount * importListing.pricePerItem
            merchant.importListings.remove(importListing)
            PlayerMerchant.save(merchant)

            sender msg green("Refunded C${importListing.desiredAmount * importListing.pricePerItem} to merchant $merchantId")
            sender msg green("Removed import listing of ${importListing.desiredAmount} ${importListing.itemString}(s)")

            return
        }

        fail { "No import listing of $itemString(s)" }
    }

    @Subcommand("import list")
    fun onImportList(sender: Player, merchantId: String) {
        val merchant = retrieveMerchantByName(sender, merchantId)

        sender msg green("Import listings:")
        for (importListing in merchant.importListings) {
            sender msg (
                    "&2${importListing.desiredAmount} ${importListing.itemString}(s) with a price per item of ${importListing.pricePerItem} (${importListing.stock} collectable items)"
                    )
        }
    }

    @Subcommand("export add")
    fun onExportCreate(sender: Player, merchantId: String, itemName: String, amount: Int, pricePerItem: Int) {
        val item: ItemStack

        try {
            item = StarshipFactories.fromItemString(itemName)
        } catch (e: Exception) {
            fail { e.message ?: "Invalid item string" }
        }

        item.amount = amount

        val merchant = retrieveMerchantByName(sender, merchantId)

        failIf(!sender.inventory.containsAtLeast(item, amount)) {
            "You don't have the required items!"
        }

        failIf(merchant.settlementId != getCurrentSettlementContext(sender).second._id) {
            "You need to be in the same settlement as $merchant!"
        }

        sender.inventory.removeItem(item)

        for (exportListing in merchant.exportListings) {
            if (exportListing.itemString == itemName) {
                exportListing.stock += amount

                sender msg green("Added $amount $itemName(s) to an existing export listing")

                PlayerMerchant.save(merchant)

                return
            }
        }

        val exportListing = PlayerMerchant.ExportListing(itemName, amount, pricePerItem)

        merchant.exportListings.add(exportListing)

        PlayerMerchant.save(merchant)

        sender msg green("Created export listing of $amount $itemName(s) with a price per item of $pricePerItem")
    }

    @Subcommand("export remove")
    fun onExportRemove(sender: Player, merchantId: String, itemName: String) {
        val item: ItemStack

        try {
            item = StarshipFactories.fromItemString(itemName)
        } catch (e: Exception) {
            fail { e.message ?: "Invalid item string" }
        }

        val merchant = retrieveMerchantByName(sender, merchantId)

        failIf(merchant.settlementId != getCurrentSettlementContext(sender).second._id) {
            "You need to be in the same settlement as $merchant!"
        }

        for (exportListing in merchant.exportListings) {
            if (exportListing.itemString == itemName) {
                repeat(exportListing.stock) {
                    sender.inventory.addItem(item)
                }
                merchant.exportListings.remove(exportListing)
                PlayerMerchant.save(merchant)

                sender msg green("Removed export listing of ${exportListing.stock} $itemName(s)")

                return
            }
        }
        fail { "No export listing of $itemName" }
    }

    @Subcommand("export list")
    fun onExportList(sender: Player, merchantId: String) {
        val merchant = retrieveMerchantByName(sender, merchantId)

        sender msg green("Export listings:")
        for (exportListing in merchant.exportListings) {
            if (exportListing.stock < 1) {
                merchant.exportListings.remove(exportListing)
            } else {
                sender msg (
                        "&2${exportListing.stock} ${exportListing.itemString}(s) with a price per item of ${exportListing.pricePerItem}"
                        )
            }
        }
    }

    @Subcommand("sync")
    fun onSync(sender: CommandSender) {
        sender msg gray("Synchronizing...")
        PlayerMerchants.synchronizeAsync {
            sender msg green("Synchronized!")
        }
    }

    private fun retrieveMerchantByName(sender: Player, merchantName: String): PlayerMerchant {
        return PlayerMerchant.findByName(merchantName, sender.slPlayerId)
            ?: fail { "Merchant $merchantName not found" }
    }

    private fun deposit(merchant: PlayerMerchant, amount: Int): Int {
        failIf(amount <= 0) { "Amount can't be negative" }
        merchant.balance += amount
        PlayerMerchant.save(merchant)
        return merchant.balance
    }

    private fun withdraw(merchant: PlayerMerchant, amount: Int): Int {
        failIf(amount <= 0) { "Amount can't be negative" }
        merchant.balance -= amount
        PlayerMerchant.save(merchant)
        return merchant.balance
    }
}
