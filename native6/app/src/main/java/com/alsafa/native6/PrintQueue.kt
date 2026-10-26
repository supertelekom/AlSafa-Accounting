package com.alsafa.native6

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/** Durable local print jobs, deliberately separate from immutable accounting ledgers. */
class PrintQueue(context:Context):SQLiteOpenHelper(context,"alsafa_print_queue.db",null,1) {
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE print_jobs(id TEXT PRIMARY KEY,invoice_id TEXT NOT NULL,invoice_number TEXT NOT NULL,created_at INTEGER NOT NULL,status TEXT NOT NULL CHECK(status IN ('pending','sending','sent_unconfirmed')),attempts INTEGER NOT NULL DEFAULT 0,last_error TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE INDEX print_jobs_status ON print_jobs(status,created_at)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
        error("Print queue migration required; existing jobs preserved")
    }
    data class Job(val id:String,val invoiceId:String,val invoiceNumber:String,val status:String,val attempts:Int,val error:String)
    fun findOutstanding(invoiceId:String):Job? =
        list().firstOrNull { it.invoiceId==invoiceId && it.status!="sent_unconfirmed" }

    fun enqueue(invoiceId:String,invoiceNumber:String):String {
        val db=writableDatabase
        val id=UUID.randomUUID().toString()
        val values=android.content.ContentValues().apply {
            put("id",id);put("invoice_id",invoiceId);put("invoice_number",invoiceNumber)
            put("created_at",System.currentTimeMillis());put("status","pending")
        }
        check(db.insertOrThrow("print_jobs",null,values)!=-1L)
        return id
    }
    fun list():List<Job> {
        val jobs=mutableListOf<Job>()
        readableDatabase.rawQuery("SELECT id,invoice_id,invoice_number,status,attempts,last_error FROM print_jobs ORDER BY created_at DESC",null).use { c ->
            while(c.moveToNext()) jobs.add(Job(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4),c.getString(5)))
        }
        return jobs
    }
    fun markSending(id:String) {
        writableDatabase.execSQL("UPDATE print_jobs SET status='sending',attempts=attempts+1,last_error='' WHERE id=? AND status IN ('pending','sending','sent_unconfirmed')",arrayOf(id))
    }
    fun markFailed(id:String,error:String) {
        writableDatabase.execSQL("UPDATE print_jobs SET status='pending',last_error=? WHERE id=?",arrayOf(error.take(300),id))
    }
    /** Transport success does not prove paper came out: require explicit user confirmation. */
    fun markSentUnconfirmed(id:String) {
        writableDatabase.execSQL("UPDATE print_jobs SET status='sent_unconfirmed',last_error='' WHERE id=?",arrayOf(id))
    }
    fun confirmPrinted(id:String) {
        writableDatabase.delete("print_jobs","id=? AND status='sent_unconfirmed'",arrayOf(id))
    }
}
