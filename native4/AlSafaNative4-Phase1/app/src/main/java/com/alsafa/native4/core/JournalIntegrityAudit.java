package com.alsafa.native4.core;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
/** Read-only diagnostics; never repairs or changes historical financial data. */
public final class JournalIntegrityAudit {
 private JournalIntegrityAudit(){}
 public static final class Result {
  public final long foreignKeyViolations,orphanLines,unbalancedEntries,emptyEntries,invalidAccounts,invalidLines,shortEntries;
  Result(long foreignKeyViolations,long orphanLines,long unbalancedEntries,long emptyEntries,long invalidAccounts,long invalidLines,long shortEntries){
   this.foreignKeyViolations=foreignKeyViolations;this.orphanLines=orphanLines;this.unbalancedEntries=unbalancedEntries;this.emptyEntries=emptyEntries;this.invalidAccounts=invalidAccounts;this.invalidLines=invalidLines;this.shortEntries=shortEntries;
  }
  public boolean isClean(){return foreignKeyViolations==0&&orphanLines==0&&unbalancedEntries==0&&emptyEntries==0&&invalidAccounts==0&&invalidLines==0&&shortEntries==0;}
 }
 private static long count(SQLiteDatabase db,String sql){
  try(Cursor c=db.rawQuery(sql,null)){if(!c.moveToFirst())throw new IllegalStateException("Audit query failed");return c.getLong(0);}
 }
 public static Result inspect(SQLiteDatabase db){
  long fk=0;
  try(Cursor c=db.rawQuery("PRAGMA foreign_key_check",null)){while(c.moveToNext())fk++;}
  long orphan=count(db,"SELECT COUNT(*) FROM journal_lines l LEFT JOIN journal_entries e ON e.entry_id=l.entry_id WHERE e.entry_id IS NULL");
  long unbalanced=count(db,"SELECT COUNT(*) FROM (SELECT e.entry_id FROM journal_entries e JOIN journal_lines l ON l.entry_id=e.entry_id GROUP BY e.entry_id HAVING SUM(l.debit_minor)<>SUM(l.credit_minor))");
  long empty=count(db,"SELECT COUNT(*) FROM journal_entries e WHERE NOT EXISTS(SELECT 1 FROM journal_lines l WHERE l.entry_id=e.entry_id)");
  long invalid=count(db,"SELECT COUNT(*) FROM journal_lines l LEFT JOIN ledger_accounts a ON a.account_code=l.account_code WHERE a.account_code IS NULL");
  long invalidLines=count(db,"SELECT COUNT(*) FROM journal_lines WHERE debit_minor<0 OR credit_minor<0 OR (debit_minor=0 AND credit_minor=0) OR (debit_minor>0 AND credit_minor>0)");
  long shortEntries=count(db,"SELECT COUNT(*) FROM (SELECT e.entry_id FROM journal_entries e JOIN journal_lines l ON l.entry_id=e.entry_id GROUP BY e.entry_id HAVING COUNT(l.line_id)<2)");
  return new Result(fk,orphan,unbalanced,empty,invalid,invalidLines,shortEntries);
 }
}
