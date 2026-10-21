"""Validate first-activity boundary for missed daily closings (SQLite semantics)."""
import sqlite3
from datetime import date,timedelta

db=sqlite3.connect(":memory:")
db.executescript("CREATE TABLE events(id TEXT,date TEXT); CREATE TABLE journal(event_id TEXT,account TEXT,currency TEXT,amount INTEGER);")
db.executemany("INSERT INTO events VALUES(?,?)",[("other","2026-09-01"),("drawer","2026-10-10"),("transfer","2026-10-11")])
db.executemany("INSERT INTO journal VALUES(?,?,?,?)",[("other","cash:bank","USD",100),("drawer","cash:drawer","USD",500),("transfer","cash:drawer","USD",-200)])
def missed_days(vault,now,lookback):
    earliest=now-timedelta(days=lookback)
    row=db.execute("SELECT min(e.date) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND e.date<?",("cash:"+vault,now.isoformat())).fetchone()[0]
    if row is None:return []
    first=max(earliest,date.fromisoformat(row))
    return [(first+timedelta(days=i)).isoformat() for i in range((now-first).days)]
assert missed_days("drawer",date(2026,10,13),31)==["2026-10-10","2026-10-11","2026-10-12"]
assert missed_days("drawer",date(2026,10,13),2)==["2026-10-11","2026-10-12"]
assert missed_days("sham",date(2026,10,13),31)==[]
assert missed_days("bank",date(2026,10,13),365)[0]=="2026-09-01"
print("PASS missed-day closing: first activity, bounded lookback, vault isolation")
