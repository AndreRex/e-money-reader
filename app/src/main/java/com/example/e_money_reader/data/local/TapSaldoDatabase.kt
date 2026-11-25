package com.example.e_money_reader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CardEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(TapSaldoConverters::class)
abstract class TapSaldoDatabase : RoomDatabase() {
    abstract fun dao(): TapSaldoDao

    companion object {
        @Volatile
        private var INSTANCE: TapSaldoDatabase? = null

        fun getInstance(context: Context): TapSaldoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TapSaldoDatabase::class.java,
                    "tapsaldo_emoney.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
