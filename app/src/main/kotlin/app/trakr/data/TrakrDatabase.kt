@file:Suppress("ktlint:standard:max-line-length", "MaxLineLength")

package app.trakr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.trakr.model.AlertEvent
import app.trakr.model.RssiSample
import app.trakr.model.ScanSession
import app.trakr.model.Tool
import app.trakr.model.ToolAlertSetting
import app.trakr.model.TrackerMute

@Database(
    entities = [Tool::class, AlertEvent::class, RssiSample::class, ToolAlertSetting::class, TrackerMute::class, ScanSession::class],
    version = 9,
    exportSchema = false,
)
abstract class TrakrDatabase : RoomDatabase() {
    abstract fun toolDao(): ToolDao
}

val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS tool_alert_settings (toolId TEXT NOT NULL PRIMARY KEY, muted INTEGER NOT NULL, sound TEXT NOT NULL, vibration INTEGER NOT NULL, importance INTEGER NOT NULL)",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS tracker_mute (address TEXT NOT NULL PRIMARY KEY, muted INTEGER NOT NULL)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS scan_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ts INTEGER NOT NULL, connectedTrackers INTEGER NOT NULL, toolsSeen INTEGER NOT NULL, toolsTotal INTEGER NOT NULL, triggeredBy TEXT NOT NULL)",
            )
        }
    }

/**
 * Container de dependÃªncias simples.
 *
 * O firmware do rastreador continua sendo a fonte da verdade; o Room aqui Ã© o
 * cache local usado para visualizaÃ§Ã£o offline e histÃ³rico.
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
            ).addMigrations(MIGRATION_8_9).fallbackToDestructiveMigration().build()
    }
}
