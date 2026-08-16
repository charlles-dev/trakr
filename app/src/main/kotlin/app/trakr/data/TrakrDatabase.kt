package app.trakr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.trakr.model.AlertEvent
import app.trakr.model.RssiSample
import app.trakr.model.Tool

@Database(
    entities = [Tool::class, AlertEvent::class, RssiSample::class],
    version = 8,
    exportSchema = false,
)
abstract class TrakrDatabase : RoomDatabase() {
    abstract fun toolboxDao(): ToolboxDao
}

/**
 * Container de dependências simples.
 *
 * O firmware do rastreador continua sendo a fonte da verdade; o Room aqui é o
 * cache local usado para visualização offline e histórico.
 */
object AppContainer {
    lateinit var database: TrakrDatabase
        private set

    fun init(context: Context) {
        database =
            Room.databaseBuilder(
                context.applicationContext,
                TrakrDatabase::class.java,
                "trakr.db",
            ).fallbackToDestructiveMigration().build()
    }
}
