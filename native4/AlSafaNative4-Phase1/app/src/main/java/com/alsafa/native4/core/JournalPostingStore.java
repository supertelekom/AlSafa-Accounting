package com.alsafa.native4.core;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;
import java.util.UUID;
/** Caller must already hold the same SQLite transaction as its business operation. */
public final class JournalPostingStore {
 private JournalPostingStore(){}
 public static final class Line {
  public final String account;public final long debit,credit;
  public Line(String account,long debit,long credit){this.account=account;this.debit=debit;this.credit=credit;}
 }
 public static void post(SQLiteDatabase db,String sourceType,String sourceId,String kind,String currency,List<Line> lines,long postedAt){
  if(!db.inTransaction())throw new IllegalStateException("Journal posting requires business transaction");
  if(sourceType==null||sourceType.trim().isEmpty()||sourceId==null||sourceId.trim().isEmpty()||kind==null||kind.trim().isEmpty())throw new IllegalArgumentException("Missing journal identity");
  String cur=MoneyRules.currency(currency);
  if(postedAt<=0)throw new IllegalArgumentException("Invalid posting time");
  if(lines==null||lines.size()<2)throw new IllegalArgumentException("At least two journal lines required");
  long debit=0,credit=0;
  for(Line line:lines){
   if(line==null||line.account==null||line.account.trim().isEmpty()||!line.account.trim().matches("[A-Z0-9_]{2,40}")||line.debit<0||line.credit<0||(line.debit==0)==(line.credit==0))throw new IllegalArgumentException("Invalid journal line");
   debit=Math.addExact(debit,line.debit);credit=Math.addExact(credit,line.credit);
  }
  if(debit==0||debit!=credit)throw new IllegalArgumentException("Unbalanced journal entry");
  try(Cursor existing=db.rawQuery("SELECT entry_id FROM journal_entries WHERE source_type=? AND source_id=? AND entry_kind=? AND currency=?",new String[]{sourceType,sourceId,kind,cur})){
   if(existing.moveToFirst())throw new IllegalStateException("Journal entry already posted");
  }
  String id="journal-"+UUID.randomUUID();
  ContentValues header=new ContentValues();header.put("entry_id",id);header.put("source_type",sourceType);header.put("source_id",sourceId);header.put("entry_kind",kind);header.put("currency",cur);header.put("posted_at",postedAt);db.insertOrThrow("journal_entries",null,header);
  for(Line line:lines){
   ContentValues v=new ContentValues();v.put("line_id","line-"+UUID.randomUUID());v.put("entry_id",id);v.put("account_code",line.account.trim());v.put("debit_minor",line.debit);v.put("credit_minor",line.credit);db.insertOrThrow("journal_lines",null,v);
  }
 }
}
