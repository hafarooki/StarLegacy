package net.starlegacy.command.nations.money

import co.aikar.commands.annotation.*
import net.starlegacy.database.schema.nations.Nation
import net.starlegacy.database.schema.nations.NationRole
import org.bukkit.entity.Player

@CommandAlias("nation|n")
internal object NationMoneyCommand : MoneyCommand<Nation>() {
    override fun requireCanDeposit(sender: Player, parent: Nation) {
        requireNationPermission(sender, parent, NationRole.Permission.MONEY_DEPOSIT)
    }

    override fun requireCanWithdraw(sender: Player, parent: Nation) {
        requireNationPermission(sender, parent, NationRole.Permission.MONEY_WITHDRAW)
    }

    override fun requireDefaultParent(sender: Player): Nation {
        return requireNationIn(sender)
    }

    override fun resolveParent(name: String): Nation = resolveNation(name)

    override fun getBalance(parent: Nation): Int {
        return Nation.findPropById(parent._id, Nation::balance) ?: fail { "Failed to retrieve nation balance" }
    }

    override fun deposit(parent: Nation, amount: Int) {
        Nation.deposit(parent._id, amount)
    }

    override fun withdraw(parent: Nation, amount: Int) {
        Nation.withdraw(parent._id, amount)
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    @Subcommand("balance|money|bal")
    @CommandCompletion("@nations")
    @Description("Check how much money a nation has")
    override fun onBalance(sender: Player, @Optional nation: String?) = super.onBalance(sender, nation)

    @Subcommand("deposit")
    @Description("Give money to your nation")
    override fun onDeposit(sender: Player, amount: Int) = super.onDeposit(sender, amount)

    @Subcommand("withdraw")
    @Description("Take money from your nation")
    override fun onWithdraw(sender: Player, amount: Int) = super.onWithdraw(sender, amount)
}
