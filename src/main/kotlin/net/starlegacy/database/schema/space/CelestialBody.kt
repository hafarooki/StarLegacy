package net.starlegacy.database.schema.space

import com.fasterxml.jackson.annotation.JsonIgnore
import net.starlegacy.database.DbObject
import net.starlegacy.database.Oid
import net.starlegacy.feature.space.celestialbody.instance.CelestialBodyInstance

abstract class CelestialBody : DbObject {
    abstract override val _id: Oid<out CelestialBody>
    abstract val name: String

    @get:JsonIgnore
    abstract val instance: CelestialBodyInstance<out CelestialBody>
}
