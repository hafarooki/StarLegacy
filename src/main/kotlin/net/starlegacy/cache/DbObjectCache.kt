package net.starlegacy.cache

import co.aikar.timings.Timing
import com.googlecode.cqengine.ConcurrentIndexedCollection
import com.googlecode.cqengine.attribute.SimpleAttribute
import com.googlecode.cqengine.attribute.SimpleNullableAttribute
import com.googlecode.cqengine.attribute.support.FunctionalSimpleAttribute
import com.googlecode.cqengine.attribute.support.FunctionalSimpleNullableAttribute
import com.googlecode.cqengine.attribute.support.SimpleFunction
import com.googlecode.cqengine.index.unique.UniqueIndex
import com.googlecode.cqengine.query.QueryFactory
import com.mongodb.client.model.changestream.ChangeStreamDocument
import net.starlegacy.database.DbObject
import net.starlegacy.database.Oid
import net.starlegacy.database.OidDbObjectCompanion
import net.starlegacy.database.oid
import net.starlegacy.util.Tasks
import net.starlegacy.util.timing
import java.util.UUID
import kotlin.reflect.KProperty1

abstract class DbObjectCache<T : DbObject>(private val companions: List<OidDbObjectCompanion<out T>>) : Cache {
    constructor(companion: OidDbObjectCompanion<T>) : this(listOf(companion))

    protected lateinit var cache: ConcurrentIndexedCollection<T>

    protected abstract val idAttribute: FunctionalSimpleAttribute<T, Oid<out T>>

    //    private val mutex = Any()
    //    private fun synced(block: () -> Unit): Unit = synchronized(mutex, block)
    private fun synced(timing: Timing, block: () -> Unit): Unit = Tasks.syncTimed(timing, block)

    private val insertTiming = timing("${javaClass.simpleName} Insert")
    private val updateTiming = timing("${javaClass.simpleName} Update")
    private val deleteTiming = timing("${javaClass.simpleName} Delete")

    override fun load() {
        cache = ConcurrentIndexedCollection()

        cache.addAll(companions.flatMap { it.all() })
        cache.addIndex(UniqueIndex.onAttribute(idAttribute))
        addExtraIndexes()

        companions.forEach { companion ->
            companion.watchInserts { change: ChangeStreamDocument<out T> ->
                val fullDocument = change.fullDocument ?: return@watchInserts
                synced(insertTiming) {
                    cache.add(fullDocument)
                    onInsert(fullDocument)
                }
            }

            companion.watchUpdates(fullDocument = true) { change: ChangeStreamDocument<out T> ->
                val fullDocument = change.fullDocument ?: return@watchUpdates
                synced(updateTiming) {
                    val cached = this[change.oid]
                    cache.remove(cached)
                    cache.add(fullDocument)
                    onUpdate(cached, fullDocument)
                }
            }

            companion.watchDeletes { change: ChangeStreamDocument<out T> ->
                synced(deleteTiming) {
                    val cached = this[change.oid]
                    cache.remove(cached)
                    onDelete(cached)
                }
            }
        }

    }

    protected open fun addExtraIndexes() {}

    protected open fun onInsert(cached: T) {}

    protected open fun onUpdate(old: T, new: T) {}

    protected open fun onDelete(cached: T) {}

    fun getAll(): List<T> = cache.toList()

    operator fun get(id: Oid<out T>): T = cache
        .retrieve(QueryFactory.equal(idAttribute, id))
        .firstOrNull() ?: error("$id not cached!")

    // this allows getting child class instances without casting
    inline fun <reified X : T> getById(id: Oid<X>): X {
        val entry = this[id]
        check(entry is X) {
            "${entry._id} is an instance of ${entry.javaClass.simpleName}, not ${X::class.java.simpleName}"
        }
        return entry
    }

    protected inline fun <reified O : T, reified A> attribute(
        noinline block: (O) -> A
    ): FunctionalSimpleAttribute<O, A> {
        return FunctionalSimpleAttribute(
            O::class.java,
            A::class.java,
            UUID.randomUUID().toString(),
            SimpleFunction(block)
        )
    }

    protected inline fun <reified O : T, reified A> nullableAttribute(
        noinline block: (T) -> A?
    ): FunctionalSimpleNullableAttribute<O, A> {
        return FunctionalSimpleNullableAttribute(
            O::class.java,
            A::class.java,
            UUID.randomUUID().toString(),
            SimpleFunction(block)
        )
    }

    // https://github.com/npgall/cqengine/blob/master/documentation/OtherJVMLanguages.md
    protected inline fun <reified O : T, reified A> propertyAttribute(
        accessor: KProperty1<O, A>
    ): FunctionalSimpleAttribute<O, A> = attribute { accessor.get(it) }

    protected inline fun <reified O : T, reified A : Oid<out T>> idAttribute(
        accessor: KProperty1<O, A>
    ): FunctionalSimpleAttribute<O, Oid<out T>> = propertyAttribute(accessor)
}
