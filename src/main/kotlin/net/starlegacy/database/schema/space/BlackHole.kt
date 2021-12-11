package net.starlegacy.database.schema.space

import com.fasterxml.jackson.annotation.JsonIgnore
import net.starlegacy.database.Oid
import net.starlegacy.database.OidDbObjectCompanion
import net.starlegacy.database.ensureUniqueIndexCaseInsensitive
import net.starlegacy.database.generateOid
import net.starlegacy.feature.space.celestialbody.instance.BlackHoleInstance
import org.litote.kmongo.set
import org.litote.kmongo.setTo

data class BlackHole(
    override val _id: Oid<BlackHole> = generateOid(),
    override val name: String,
    val spaceWorld: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val radius: Int,
) : CelestialBody() {
    companion object : OidDbObjectCompanion<BlackHole>(BlackHole::class, setup = {
        ensureUniqueIndexCaseInsensitive(BlackHole::name)
    }) {
        fun create(
            name: String,
            spaceWorld: String,
            x: Int,
            y: Int,
            z: Int,
            radius: Int
        ): Oid<BlackHole> {
            val id = generateOid<BlackHole>()
            col.insertOne(BlackHole(id, name, spaceWorld, x, y, z, radius))
            return id
        }

        fun setPos(id: Oid<BlackHole>, spaceWorld: String, x: Int, y: Int, z: Int) {
            updateById(
                id,
                set(
                    BlackHole::spaceWorld setTo spaceWorld,
                    BlackHole::x setTo x,
                    BlackHole::y setTo y,
                    BlackHole::z setTo z
                )
            )
        }
    }

    @delegate:JsonIgnore
    override val instance by lazy { BlackHoleInstance(this) }
}
