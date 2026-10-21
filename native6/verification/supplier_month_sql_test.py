import sqlite3
db=sqlite3.connect(":memory:")
db.executescript("CREATE TABLE events(id TEXT,date TEXT,created INTEGER,kind TEXT);CREATE TABLE journal(event_id TEXT,account TEXT,currency TEXT,amount INTEGER);")
data=[("a","2026-09-10",-10000),("b","2026-10-02",-5000),("c","2026-10-03",4000),("d","2026-10-04",-2000),("e","2026-11-01",3000)]
for i,(name,day,amount) in enumerate(data):
 db.execute("INSERT INTO events VALUES(?,?,?,?)",(name,day,i,"TEST"))
 db.execute("INSERT INTO journal VALUES(?,?,?,?)",(name,"payable:s1","USD",amount))
args=("payable:s1","USD","2026-10-01","2026-11-01")
opening=-db.execute("SELECT coalesce(sum(j.amount),0) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date<?",args[:3]).fetchone()[0]
rows=db.execute("SELECT e.date,e.kind,e.id event_id,coalesce(sum(j.amount),0) amount FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<? GROUP BY e.id,e.date,e.kind ORDER BY e.date,e.created,e.id",args).fetchall()
balance=opening
for row in rows:balance-=row[3]
assert opening==10000 and balance==13000 and len(rows)==3
for sign,expected in [("<0",7000),(">0",4000)]:
 values=db.execute("SELECT coalesce(sum(j.amount),0) amount FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<? GROUP BY e.id HAVING sum(j.amount)"+sign,args).fetchall()
 assert sum(abs(v[0]) for v in values)==expected
# A backdated purchase belongs to its transaction month, not its creation month.
db.execute("INSERT INTO events VALUES(?,?,?,?)",("late","2026-10-08",99,"PURCHASE"))
db.execute("INSERT INTO journal VALUES(?,?,?,?)",("late","payable:s1","USD",-1500))
monthly=db.execute("SELECT coalesce(sum(j.amount),0) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<?",args).fetchone()[0]
assert monthly==-4500
assert opening-monthly==14500
# Foreign suppliers and other currencies must not alter this supplier's USD ledger.
db.execute("INSERT INTO events VALUES(?,?,?,?)",("other","2026-10-09",100,"PURCHASE"))
db.execute("INSERT INTO journal VALUES(?,?,?,?)",("other","payable:s2","USD",-90000))
db.execute("INSERT INTO events VALUES(?,?,?,?)",("try","2026-10-10",101,"PURCHASE"))
db.execute("INSERT INTO journal VALUES(?,?,?,?)",("try","payable:s1","TRY",-50000))
isolated=db.execute("SELECT coalesce(sum(j.amount),0) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date>=? AND e.date<?",args).fetchone()[0]
assert isolated==-4500
print("PASS supplier monthly ledger SQL including backdated posting, supplier and currency isolation")
