package androidx.room
annotation class Entity(val tableName:String="",val indices:Array<Index> = [])
annotation class Index(val value:Array<String>,val name:String="",val unique:Boolean=false)
annotation class PrimaryKey
annotation class ColumnInfo(val defaultValue:String="")
suspend fun <T> com.alsafa.native8.sales.AppDatabase.withTransaction(block:suspend ()->T):T {val saved=snapshot();try{return block()}catch(t:Throwable){restore(saved);throw t}}
