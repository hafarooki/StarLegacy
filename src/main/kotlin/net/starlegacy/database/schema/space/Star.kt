package net.starlegacy.database.schema.space

import com.fasterxml.jackson.annotation.JsonIgnore
import net.starlegacy.database.Oid
import net.starlegacy.database.OidDbObjectCompanion
import net.starlegacy.database.ensureUniqueIndexCaseInsensitive
import net.starlegacy.database.generateOid
import net.starlegacy.feature.space.celestialbody.StarClassification
import net.starlegacy.feature.space.celestialbody.instance.StarInstance
import org.litote.kmongo.set
import org.litote.kmongo.setTo

data class Star(
    override val _id: Oid<Star> = generateOid(),
    override val name: String,
    val spaceWorld: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val classification: StarClassification,
) : CelestialBody() {
    companion object : OidDbObjectCompanion<Star>(Star::class, setup = {
        ensureUniqueIndexCaseInsensitive(Star::name)
    }) {
        fun create(
            name: String,
            spaceWorld: String,
            x: Int,
            y: Int,
            z: Int,
            classification: StarClassification
        ): Oid<Star> {
            val id = generateOid<Star>()
            col.insertOne(Star(id, name, spaceWorld, x, y, z, classification))
            return id
        }

        fun setPos(id: Oid<Star>, spaceWorld: String, x: Int, y: Int, z: Int) {
            updateById(id, set(Star::spaceWorld setTo spaceWorld, Star::x setTo x, Star::y setTo y, Star::z setTo z))
        }
    }

    @delegate:JsonIgnore
    override val instance by lazy { StarInstance(this) }
}
