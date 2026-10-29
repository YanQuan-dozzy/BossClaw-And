package com.bossclaw.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        JobEntity::class,
        ChatMessageEntity::class,
        LogEntity::class,
        LlmAuditEntity::class,
        ProfileEntity::class,
        DirectionEntity::class,
        CompanyEntity::class,
        SkillEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BossClawDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun chatDao(): ChatDao
    abstract fun logDao(): LogDao
    abstract fun auditDao(): AuditDao
    abstract fun profileDao(): ProfileDao
    abstract fun directionDao(): DirectionDao
    abstract fun companyDao(): CompanyDao
    abstract fun skillDao(): SkillDao

    companion object {
        @Volatile
        private var instance: BossClawDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `skills` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                        "`scope` TEXT NOT NULL, `instructions` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                        "`builtin` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_skills_scope` ON `skills` (`scope`)")
            }
        }

        fun get(context: Context): BossClawDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                BossClawDatabase::class.java,
                "bossclaw.db"
            ).addMigrations(MIGRATION_1_2).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
