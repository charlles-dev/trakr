@file:Suppress("ktlint:standard:max-line-length", "MaxLineLength")

package app.trakr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.trakr.model.AlertEvent
import app.trakr.model.JobKit
import app.trakr.model.RssiSample
import app.trakr.model.ScanSession
import app.trakr.model.Tool
import app.trakr.model.ToolAlertSetting
import app.trakr.model.ToolEvent
import app.trakr.model.TrackerMute

@Database(
    entities = [
        Tool::class,
        AlertEvent::class,
        RssiSample::class,
        ToolAlertSetting::class,
        TrackerMute::class,
        ScanSession::class,
        JobKit::class,
        ToolEvent::class,
    ],
    version = 11,
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

val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tools ADD COLUMN category TEXT NOT NULL DEFAULT 'manual'")
        }
    }

val MIGRATION_10_11 =
    object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS job_kits (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL, toolIdsCsv TEXT NOT NULL, createdAt INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS tool_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, toolId TEXT NOT NULL, timestamp INTEGER NOT NULL, eventType TEXT NOT NULL, details TEXT NOT NULL, rssi INTEGER)",
            )
        }
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
            ).addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).fallbackToDestructiveMigration().build()
    }
}
