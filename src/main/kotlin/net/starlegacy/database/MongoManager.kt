package net.starlegacy.database

import com.mongodb.ConnectionString
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoCursor
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.changestream.ChangeStreamDocument
import net.starlegacy.SETTINGS
import net.starlegacy.SLComponent
import net.starlegacy.database.schema.economy.*
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.misc.Shuttle
import net.starlegacy.database.schema.nations.*
import net.starlegacy.database.schema.space.*
import net.starlegacy.database.schema.starships.Blueprint
import net.starlegacy.database.schema.starships.Platform
import net.starlegacy.database.schema.starships.PlayerStarship
import net.starlegacy.util.Tasks
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.Document
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.json.JsonReader
import org.litote.kmongo.KMongo
import org.litote.kmongo.id.IdGenerator
import org.litote.kmongo.id.ObjectIdGenerator
import org.litote.kmongo.util.KMongoUtil
import java.util.concurrent.Executors
import kotlin.reflect.KClass

object MongoManager : SLComponent() {
    private val watching = mutableListOf<MongoCursor<ChangeStreamDocument<*>>>()

    internal lateinit var client: MongoClient

    @PublishedApi // to allow it to be used in inline functions
    internal lateinit var database: MongoDatabase

    val threadPool = Executors.newCachedThreadPool(Tasks.namedThreadFactory("starlegacy-mongodb-cache"))

    override fun onEnable() {
        IdGenerator.defaultGenerator = ObjectIdGenerator

        System.setProperty(
            "org.litote.mongo.test.mapping.service",
            "org.litote.kmongo.jackson.JacksonClassMappingTypeService"
        )

        client = createClient()

        database = client.getDatabase(SETTINGS.mongo.database)

        // ##### Load classes of all collections #####

        // misc
        SLPlayer.init()
        Shuttle.init()

        // nations
        Territory.init()
        Nation.init()
        NationRelation.init()
        SettlementRole.init()
        NationRole.init()
        Settlement.init()
        SettlementZone.init()
        NationOutpost.init()

        // space
        Planet.init()
        Star.init()
        BlackHole.init()
        CosmicBarrier.init()
        SpaceZone.init()

        // economy
        CollectedItem.init()
        EcoStation.init()
        PlayerMerchant.init()

        // starships
        PlayerStarship.init()
        Platform.init()
        Blueprint.init()
    }

    private fun createClient(): MongoClient {
        if (SETTINGS.mongo.authenticate) {
            val username = SETTINGS.mongo.username
            val password = SETTINGS.mongo.password
            val host = SETTINGS.mongo.host
            val port = SETTINGS.mongo.port
            val authDb = SETTINGS.mongo.database
            val connectionString = ConnectionString("mongodb://$username:$password@$host:$port/$authDb")
            return KMongo.createClient(connectionString)
        }

        val host = SETTINGS.mongo.host
        val port = SETTINGS.mongo.port
        val authDb = SETTINGS.mongo.database
        val connectionString = ConnectionString("mongodb://$host:$port/$authDb")
        return KMongo.createClient(connectionString)
    }

    override fun onDisable() {
        if (::client.isInitialized) {
            client.close()
        }
    }

    inline fun <reified T> decode(document: Document): T =
        decode(document.toBsonDocument(T::class.java, database.codecRegistry))

    inline fun <reified T> decode(document: BsonDocument): T {
        val codecRegistry: CodecRegistry = database.codecRegistry
        val clazz: Class<T> = T::class.java
        BsonDocumentReader(document).use { reader ->
            return codecRegistry.get(clazz).decode(reader, DecoderContext.builder().build())
        }
    }

    inline fun <reified T> decode(json: String): T {
        val codecRegistry: CodecRegistry = database.codecRegistry

        val clazz: Class<T> = T::class.java

        JsonReader(json).use { reader ->
            return codecRegistry.get(clazz).decode(reader, DecoderContext.builder().build())
        }
    }

    internal fun <T : Any> getCollection(clazz: KClass<T>): MongoCollection<T> {
        try {
            val collectionName: String = KMongoUtil.defaultCollectionName(clazz)

            if (!database.listCollectionNames().contains(collectionName)) {
                database.createCollection(collectionName)
                log.info("Created collection $collectionName")
            }

            require(database.listCollectionNames().contains(collectionName))

            return database.getCollection(collectionName, clazz.java)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    internal fun registerWatching(cursor: MongoCursor<ChangeStreamDocument<*>>) {
        watching.add(cursor)
    }

    fun closeWatch(cursor: MongoCursor<ChangeStreamDocument<*>>) {
        watching.remove(cursor)
        cursor.close()
    }
}
