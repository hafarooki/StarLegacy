package net.starlegacy.command.nations

import net.starlegacy.command.SLCommand
import net.starlegacy.database.schema.nations.NationRelation
import net.starlegacy.database.schema.nations.NationRole
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.Subcommand
import net.starlegacy.util.Notify
import net.starlegacy.util.msg
import org.bukkit.entity.Player
import org.litote.kmongo.eq

@CommandAlias("nation|n")
internal object NationRelationCommand : SLCommand() {
    @Subcommand("ally")
    @CommandCompletion("@nations")
    fun onAlly(sender: Player, nation: String) = setRelationWish(sender, nation, NationRelation.Level.ALLY)

    @Subcommand("neutral")
    @CommandCompletion("@nations")
    fun onNeutral(sender: Player, nation: String) = setRelationWish(sender, nation, NationRelation.Level.NEUTRAL)

    @Subcommand("enemy")
    @CommandCompletion("@nations")
    fun onEnemy(sender: Player, nation: String) = setRelationWish(sender, nation, NationRelation.Level.ENEMY)

    private fun setRelationWish(sender: Player, nation: String, wish: NationRelation.Level) = asyncCommand(sender) {
        val senderNation = requireNationIn(sender)
        requireNationPermission(sender, senderNation, NationRole.Permission.MANAGE_RELATIONS)
        val otherNation = resolveNation(nation)

        val otherWish = NationRelation.getRelationWish(otherNation._id, senderNation._id)

        val actual = NationRelation.changeRelationWish(senderNation._id, otherNation._id, wish)

        Notify.online(
            "&e${sender.name} of ${senderNation.name} " +
                    "has made the relation wish &r${wish.coloredName}&e " +
                    "with the nation ${otherNation.name}. " +
                    "Their wish is &r${otherWish.coloredName}&e, " +
                    "so their relation is &r${actual.coloredName}&e!"
        )
    }

    @Subcommand("relations")
    fun onRelations(sender: Player) = asyncCommand(sender) {
        val nation = requireNationIn(sender)

        for (relation in NationRelation.find(NationRelation::nationId eq nation._id)) {
            val other = relation.otherId
            val otherName = getNationName(other)
            val otherWish = NationRelation.getRelationWish(other, nation._id)
            sender msg "&e$otherName&8: ${relation.actual.coloredName} " +
                    "&8(&7Your wish: ${relation.wish.coloredName}&7, their wish: ${otherWish.coloredName}&8)"
        }
    }
}
