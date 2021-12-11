package net.starlegacy.feature.economy.merchant.menus

import com.github.stefvanschie.inventoryframework.GuiItem
import net.starlegacy.database.schema.economy.PlayerMerchant
import net.starlegacy.database.schema.nations.Settlement
import net.starlegacy.database.schema.space.Planet
import net.starlegacy.feature.nations.gui.*
import net.starlegacy.feature.starship.factory.StarshipFactories
import net.starlegacy.util.MenuHelper.openPaginatedMenu
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

fun openPlanetBrowseMenu(merchants: List<PlayerMerchant>, planet: Planet, player: Player, itemName: String?): String? {
    val merchantItems = mutableListOf<GuiItem>()

    val importItems = mutableListOf<GuiItem>()
    val exportItems = mutableListOf<GuiItem>()

    for(merchant in merchants) {
        for(importListing in merchant.importListings) {
                    if(itemName == null && importListing.desiredAmount > 0) {
                        val importItem = StarshipFactories.fromItemString(importListing.itemString)
                        importItem.lore = listOf(
                                "Sell Price: ${importListing.pricePerItem}",
                                "Amount Listed: ${importListing.desiredAmount}"
                        )
                        importItems.add(GuiItem(importItem) {
                            player.openPaginatedMenu("Import Listings of ${merchant.name}", importItems)
                        })
                    }
                    if(itemName == importListing.itemString && importListing.desiredAmount > 0) {
                        val importItem = StarshipFactories.fromItemString(importListing.itemString)
                        importItem.lore = listOf(
                                "Sell Price: ${importListing.pricePerItem}",
                                "Amount Listed: ${importListing.desiredAmount}"
                        )
                        importItems.add(guiButton(importItem) {
                            if(isRightClick) { player.openPaginatedMenu("Import Listings of ${merchant.name}", importItems) }

                        })
                    }
                }
        for(exportListing in merchant.exportListings) {
            if(itemName == null) {
                val exportItem = StarshipFactories.fromItemString(exportListing.itemString)
                exportItem.lore = listOf(
                        "Buy Price: ${exportListing.pricePerItem}",
                        "Stock: ${exportListing.stock}"
                )
                exportItems.add(GuiItem(exportItem) {
                    player.openPaginatedMenu("Export listings of ${merchant.name}", exportItems)
                })
            }
            if(itemName == exportListing.itemString) {
                val exportItem = StarshipFactories.fromItemString(exportListing.itemString)
                exportItem.lore = listOf(
                        "Buy Price: ${exportListing.pricePerItem}",
                        "Stock: ${exportListing.stock}"
                )
                exportItems.add(guiButton(exportItem) {
                    player.openPaginatedMenu("Export listings of ${merchant.name}", exportItems)
                })
            }
        }
        if(itemName != null && exportItems.isEmpty() && importItems.isEmpty()) {
            return "No $itemName(s) in planet ${planet.name}"
        }

        val merchantItem = ItemStack(Material.CHEST, 1)
        merchantItem.lore = listOf(
                "Coords: x${merchant.x}, y${merchant.y}, z${merchant.z}",
                "Left Click: View export listings",
                "Right Click: View import listings"
        )
        merchantItems.add(guiButton(merchantItem) {
            if(isLeftClick) { player.openPaginatedMenu("Export listings of ${merchant.name}", exportItems) }
            if(isRightClick) { player.openPaginatedMenu("Import Listings of ${merchant.name}", importItems) }
        }.name(merchant.name))
    }
    player.openPaginatedMenu("Merchants in ${planet.name}", merchantItems)

    return null
}

fun merchantBrowse(sender: Player) {
    val merchants = PlayerMerchant.col.find().toList()

    val planets = Planet.col.find().toList()

    val planetItems = mutableListOf<GuiItem>()

    // top bar buttons
    for(planet in planets) {
        val planetItem: ItemStack = try {
            StarshipFactories.fromItemString("planet_icon_${planet.name}")
        } catch (e: Exception) {
            ItemStack(Material.CLOCK, 1)
        }

        val planetMerchants = mutableListOf<PlayerMerchant>()

        for(merchant in merchants) {
            val settlement = Settlement.findById(merchant.settlementId)
            if(settlement?.worldName == planet.planetWorld) {
                planetMerchants.add(merchant)
            }
        }

        planetItem.lore = listOf("Left Click: View all merchants", "Right Click: Search merchants by item", "(Vanilla items all caps, custom items no caps)")

        planetItems.add(guiButton(planetItem) {
            if(isRightClick) {
                playerClicker.inputs(
                        AnvilInput("Search Items (Vanilla items all caps, custom items no caps)") { _, r ->
                            try {
                                StarshipFactories.fromItemString(r)
                            } catch (e: Exception) {
                                return@AnvilInput "Invalid item ID"
                            }

                            return@AnvilInput openPlanetBrowseMenu(planetMerchants, planet, sender, r)
                        })
                    } else openPlanetBrowseMenu(planetMerchants, planet, sender, null)
                })
    }

    sender.openPaginatedMenu("Browse Planets", planetItems)
}
