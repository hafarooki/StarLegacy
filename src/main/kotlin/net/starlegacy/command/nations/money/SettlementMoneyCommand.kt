package net.starlegacy.command.nations.money

import co.aikar.commands.annotation.*
import net.starlegacy.database.schema.nations.Settlement
import net.starlegacy.database.schema.nations.SettlementRole
import org.bukkit.entity.Player

@CommandAlias("settlement|s")
internal object SettlementMoneyCommand : MoneyCommand<Settlement>() {
    override fun requireCanDeposit(sender: Player, parent: Settlement) {
        requireSettlementPermission(sender, parent, SettlementRole.Permission.MONEY_DEPOSIT)
    }

    override fun requireCanWithdraw(sender: Player, parent: Settlement) {
        requireSettlementPermission(sender, parent, SettlementRole.Permission.MONEY_WITHDRAW)
    }

    override fun requireDefaultParent(sender: Player): Settlement {
        return requireSettlementIn(sender)
    }

    override fun resolveParent(name: String): Settlement = resolveSettlement(name)

    override fun getBalance(parent: Settlement): Int {
        return Settlement.findPropById(parent._id, Settlement::balance)
            ?: fail { "Failed to retrieve settlement balance" }
    }

    override fun deposit(parent: Settlement, amount: Int) {
        Settlement.deposit(parent._id, amount)
    }

    override fun withdraw(parent: Settlement, amount: Int) {
        Settlement.withdraw(parent._id, amount)
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    @Subcommand("balance|money|bal")
    @CommandCompletion("@settlements")
    @Description("Check how much money a settlement has")
    override fun onBalance(sender: Player, @Optional settlement: String?) = super.onBalance(sender, settlement)

    @Subcommand("deposit")
    @Description("Give money to your settlement")
    override fun onDeposit(sender: Player, amount: Int) = super.onDeposit(sender, amount)

    @Subcommand("withdraw")
    @Description("Take money from your settlement")
    override fun onWithdraw(sender: Player, amount: Int) = super.onWithdraw(sender, amount)
}
