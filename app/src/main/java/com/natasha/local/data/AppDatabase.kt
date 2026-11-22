package com.natasha.local.data
import android.content.Context
import androidx.room.*
@Database(entities=[MessageEntity::class,MemoryEntity::class,ProjectEntity::class,AssetEntity::class,CharacterProfileEntity::class,ModelEntity::class],version=2,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){ abstract fun messages():MessageDao; abstract fun memories():MemoryDao; abstract fun projects():ProjectDao; abstract fun assets():AssetDao; abstract fun character():CharacterDao; abstract fun models():ModelDao
companion object { @Volatile private var instance:AppDatabase?=null; fun get(c:Context)=instance?:synchronized(this){instance?:Room.databaseBuilder(c,AppDatabase::class.java,"natasha.db").fallbackToDestructiveMigration().build().also{instance=it}} } }