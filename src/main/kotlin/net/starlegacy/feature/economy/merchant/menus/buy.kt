package net.starlegacy.feature.economy.merchant.menus

import com.github.stefvanschie.inventoryframework.GuiItem
import net.starlegacy.database.schema.economy.PlayerMerchant
import net.starlegacy.feature.nations.gui.AnvilInput
import net.starlegacy.feature.nations.gui.guiButton
import net.starlegacy.feature.nations.gui.inputs
import net.starlegacy.feature.nations.gui.playerClicker
import net.starlegacy.feature.starship.factory.StarshipFactories
import net.starlegacy.util.MenuHelper.openPaginatedMenu
import net.starlegacy.util.VAULT_ECO
import net.starlegacy.util.colorize
import net.starlegacy.util.getMoneyBalance
import net.starlegacy.util.toCreditsString
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

fun openBuyMenu(player: Player, merchant: PlayerMerchant) {
    val exportListings = mutableListOf<GuiItem>()

    for (exportListing in merchant.exportListings) {
        val item = StarshipFactories.fromItemString(exportListing.itemString)
        val guiItem = StarshipFactories.fromItemString(exportListing.itemString)
        guiItem.lore = listOf(
                "Price (per item): ${exportListing.pricePerItem}", "Stock: ${exportListing.stock}"
        )

        exportListings.add(guiButton(guiItem) {
            playerClicker.inputs(
                    AnvilInput("Amount to buy:") { _, r ->
                        if ((r.toIntOrNull() ?: return@AnvilInput "Must be a number")
                                !in 0..exportListing.stock
                        ) return@AnvilInput "Must be within 0 and ${exportListing.stock}" else {
                            return@AnvilInput buyItem(r, exportListing, player, item, merchant)
                        }})
        })
    }
    player.openPaginatedMenu("Buy Items", exportListings.toList())
}

private fun buyItem(r: String, exportListing: PlayerMerchant.ExportListing, player: Player, item: ItemStack, merchant: PlayerMerchant): String? {
    val amountToPay = r.toInt() * exportListing.pricePerItem
    if (player.getMoneyBalance() >= amountToPay) {
        exportListing.stock -= r.toInt()
        if (exportListing.stock < 1) {
            merchant.exportListings.remove(exportListing
            )
        }

        item.amount = r.toInt()
        player.inventory.addItem(item)
        player.sendMessage(
                "&aBought ${r.toInt()} ${exportListing.itemString}(s) for ${amountToPay.toCreditsString()}".colorize()
        )

        merchant.balance += amountToPay
        PlayerMerchant.save(merchant)
    } else return "Not enough credits"

    VAULT_ECO.withdrawPlayer(player, amountToPay.toDouble())
    return null
}
