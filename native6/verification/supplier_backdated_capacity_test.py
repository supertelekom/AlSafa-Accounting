"""Regression for intermediate-day supplier payable overdrafts."""
import sqlite3

db=sqlite3.connect(":memory:")
db.executescript("""CREATE TABLE events(id TEXT PRIMARY KEY,date TEXT);
CREATE TABLE journal(event_id TEXT,account TEXT,currency TEXT,amount INTEGER);""")
db.executemany("INSERT INTO events VALUES(?,?)",[
 ("open","2026-10-01"),("pay","2026-10-03"),
 ("buy","2026-10-08")])
db.executemany("INSERT INTO journal VALUES(?,?,?,?)",[
 ("open","payable:s","USD",-10000),
 ("pay","payable:s","USD",8000),
 ("buy","payable:s","USD",-12000)])
def available(day):
    acc="payable:s"
    balance=-db.execute("""SELECT coalesce(sum(j.amount),0)
       FROM journal j JOIN events e ON e.id=j.event_id
       WHERE j.account=? AND j.currency='USD' AND e.date<=?""",
       (acc,day)).fetchone()[0]
    minimum=balance
    for (amount,) in db.execute("""SELECT sum(j.amount)
       FROM journal j JOIN events e ON e.id=j.event_id
       WHERE j.account=? AND j.currency='USD' AND e.date>?
       GROUP BY e.date ORDER BY e.date""",(acc,day)):
        balance-=amount
        minimum=min(minimum,balance)
    return minimum
assert available("2026-10-02")==2000
assert available("2026-10-03")==2000
assert available("2026-10-08")==14000
assert available("2026-10-10")==14000
assert available("2026-09-30")==0
db.execute("INSERT INTO events VALUES(?,?)",("other","2026-10-04"))
db.execute("INSERT INTO journal VALUES(?,?,?,?)",("other","payable:another","USD",-900000))
db.execute("INSERT INTO journal VALUES(?,?,?,?)",("other","payable:s","TRY",-700000))
assert available("2026-10-02")==2000
assert available("2026-10-08")==14000
print("PASS supplier payment capacity respects intermediate daily balances")
