"""Supplier statement regression: historic debt and dated payments stay separate."""
import sqlite3
db=sqlite3.connect(":memory:")
db.executescript("""CREATE TABLE events(id TEXT,date TEXT,created INTEGER,kind TEXT);
CREATE TABLE journal(event_id TEXT,account TEXT,currency TEXT,amount INTEGER);""")
db.executemany("INSERT INTO events VALUES(?,?,?,?)",[
 ("old","2026-09-20",1,"SUPPLIER_OPENING"),
 ("buy","2026-10-02",2,"PURCHASE"),
 ("pay","2026-10-03",3,"SUPPLIER_PAYMENT"),
 ("other","2026-10-03",4,"SUPPLIER_OPENING"),
 ("nov","2026-11-01",5,"SUPPLIER_PAYMENT")])
db.executemany("INSERT INTO journal VALUES(?,?,?,?)",[
 ("old","payable:one","USD",-10000),("buy","payable:one","USD",-5000),
 ("pay","payable:one","USD",3000),("other","payable:two","USD",-1000),
 ("nov","payable:one","USD",2000)])
def statement(supplier,currency,start,end):
    acc="payable:"+supplier
    opening=-db.execute("""SELECT coalesce(sum(j.amount),0) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date<?""",(acc,currency,start)).fetchone()[0]
    rows=db.execute("""SELECT e.id,e.date,sum(j.amount) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<=? GROUP BY e.id,e.date ORDER BY e.date,e.created,e.id""",(acc,currency,start,end)).fetchall()
    running=opening
    history=[]
    for event,day,amount in rows:
        running-=amount
        history.append((event,day,running))
    closing=-db.execute("""SELECT coalesce(sum(j.amount),0) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date<=?""",(acc,currency,end)).fetchone()[0]
    assert running==closing
    return opening,history,closing
assert statement("one","USD","2026-10-01","2026-10-31")==(
 10000,[("buy","2026-10-02",15000),("pay","2026-10-03",12000)],12000)
assert statement("one","USD","2026-11-01","2026-11-30")==(
 12000,[("nov","2026-11-01",10000)],10000)
assert statement("one","TRY","2026-10-01","2026-10-31")==(0,[],0)
assert statement("one","USD","2026-10-03","2026-10-03")==(
 15000,[("pay","2026-10-03",12000)],12000)
assert statement("one","USD","2026-10-04","2026-10-31")==(
 12000,[],12000)
assert statement("two","USD","2026-10-01","2026-10-31")==(
 0,[("other","2026-10-03",1000)],1000)

print("PASS supplier running debt: historic carry, actual payment dates, supplier and currency isolation")
