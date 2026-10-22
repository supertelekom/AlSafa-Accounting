"""Regression scenarios for chronological multi-currency cash statements."""
import sqlite3
from collections import defaultdict

db=sqlite3.connect(":memory:")
db.executescript("""CREATE TABLE events(id TEXT PRIMARY KEY,date TEXT,created INTEGER,kind TEXT,memo TEXT);
CREATE TABLE journal(event_id TEXT,account TEXT,currency TEXT,amount INTEGER);""")
events=[("opening","2026-09-30",1,"OPENING","opening"),("sale","2026-10-01",2,"SALE","sale"),("transfer","2026-10-02",3,"TRANSFER","transfer"),("return","2026-10-02",4,"REFUND","refund"),("try","2026-10-02",5,"OPENING","lira")]
db.executemany("INSERT INTO events VALUES(?,?,?,?,?)",events)
db.executemany("INSERT INTO journal VALUES(?,?,?,?)",[
 ("opening","cash:drawer","USD",10000),
 ("sale","cash:drawer","USD",5000),
 ("transfer","cash:drawer","USD",-3000),
 ("transfer","cash:external","USD",3000),
 ("return","cash:drawer","USD",-1000),
 ("try","cash:drawer","TRY",75000)])
def statement(vault,currency,start,end):
    account="cash:"+vault
    opening=db.execute("""SELECT coalesce(sum(j.amount),0) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date<?""",(account,currency,start)).fetchone()[0]
    rows=db.execute("""SELECT e.date,e.id,sum(j.amount) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<=? GROUP BY e.id,e.date ORDER BY e.date,e.created,e.id""",(account,currency,start,end)).fetchall()
    running=opening
    history=[]
    for day,event,amount in rows:
        running+=amount
        history.append((event,running))
    closing=db.execute("""SELECT coalesce(sum(j.amount),0) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date<=?""",(account,currency,end)).fetchone()[0]
    assert running==closing
    return opening,history,closing
assert statement("drawer","USD","2026-10-01","2026-10-02")==(
 10000,[("sale",15000),("transfer",12000),("return",11000)],11000)
assert statement("drawer","TRY","2026-10-01","2026-10-02")==(
 0,[("try",75000)],75000)
assert statement("external","USD","2026-10-01","2026-10-02")==(
 0,[("transfer",3000)],3000)
assert statement("drawer","USD","2026-10-03","2026-10-03")==(
 11000,[],11000)
print("PASS vault running balances: carry, chronological movements, transfers, currencies")
