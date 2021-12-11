package net.starlegacy.feature.nations

import net.starlegacy.SLComponent
import net.starlegacy.util.loadConfig

lateinit var NATIONS_BALANCE: NationsBalancing.Config

object NationsBalancing : SLComponent() {
    data class Config(
        val settlement: Settlements = Settlements(),
        val nation: Nations = Nations(),
        val capture: Territories = Territories()
    ) {
        data class Settlements(
            val creationCost: Int = 10000,
            val chunkCost: Int = 500,
            val chunkUpkeepCost: Int = 1,
            val activityDays: Int = 6,
            val cityHourlyTax: Int = 125,
            val cityMinActive: Int = 4,
            val hourlyActivityCredits: Int = 1,
            val inactivityDays: Int = 30,
            val minCreateLevel: Int = 3,
            val renameCost: Int = 1500,
            val maxTaxPercent: Int = 12,
        )

        data class Nations(
            val minCreateLevel: Int = 12,
            val minJoinLevel: Int = 7,
            val hourlyActivityCredits: Int = 4,
            val createCost: Int = 20000,
            val renameCost: Int = 3000,
            val costPerOutpostBlock: Double = 0.5,
            val outpostChunkUpkeepCost: Int = 1
        )

        data class Territories(
            val siegeIntervalDays: Long = 7,
            val siegeCost: Int = 1000,
            val hourlyIncome: Int = 20,
            val daysPerSiege: Double = 0.5,
            val siegerReward: Double = 2000.0,
        )
    }

    override fun onEnable() {
        reload()
    }

    fun reload() {
        NATIONS_BALANCE = loadConfig(plugin.sharedDataFolder, "nations_balancing")
    }

    override fun supportsVanilla(): Boolean {
        return true
    }
}
