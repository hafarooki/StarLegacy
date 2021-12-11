package net.starlegacy.feature.economy.merchant.menus

import com.github.stefvanschie.inventoryframework.GuiItem
import net.starlegacy.database.schema.economy.PlayerMerchant
import net.starlegacy.feature.nations.gui.*
import net.starlegacy.feature.starship.factory.StarshipFactories
import net.starlegacy.util.MenuHelper.openPaginatedMenu
import net.starlegacy.util.VAULT_ECO
import net.starlegacy.util.colorize
import net.starlegacy.util.toCreditsString
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

fun openSellMenu(player: Player, merchant: PlayerMerchant) {
    val importListings = mutableListOf<GuiItem>()

    for (importListing in merchant.importListings) {
        if(importListing.desiredAmount < 1) { continue }

        val item = StarshipFactories.fromItemString(importListing.itemString)
        val guiItem = StarshipFactories.fromItemString(importListing.itemString)
        guiItem.lore = listOf("Price (per item): ${importListing.pricePerItem}", "Amount Listed: ${importListing.desiredAmount}")

        importListings.add(guiButton(guiItem) {
            playerClicker.inputs(
                    AnvilInput("Amount to sell:") { _, r ->
                        if ((r.toIntOrNull() ?: return@AnvilInput "Must be a number")
                                !in 1..importListing.desiredAmount
                        ) return@AnvilInput "Must be within 1 and ${importListing.desiredAmount}" else {
                            return@AnvilInput sellItem(r, importListing, player, item, merchant)
                        }})
        })
    }
    player.openPaginatedMenu("Sell Items", importListings.toList())
}

private fun sellItem(r: String, importListing: PlayerMerchant.ImportListing, player: Player, item: ItemStack, merchant: PlayerMerchant): String? {
    val amountToPay = r.toInt() * importListing.pricePerItem
    if (!player.inventory.containsAtLeast(item, r.toInt())) return "Not enough items"
    else {
        importListing.desiredAmount -= r.toInt()
        item.amount = r.toInt()

        player.inventory.removeItem(item)

        player.sendMessage(
                "&aSold ${r.toInt()} ${importListing.itemString}(s) for ${amountToPay.toCreditsString()}".colorize()
        )

        importListing.stock += r.toInt()
        PlayerMerchant.save(merchant)

        VAULT_ECO.depositPlayer(player, amountToPay.toDouble())
    }

    return null
}

