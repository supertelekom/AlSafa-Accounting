import sqlite3

db=sqlite3.connect(':memory:')
db.executescript("""CREATE TABLE events(id TEXT PRIMARY KEY,date TEXT NOT NULL,kind TEXT NOT NULL,created INTEGER NOT NULL,memo TEXT);
CREATE TABLE products(id TEXT PRIMARY KEY,name TEXT,currency TEXT);
CREATE TABLE stock_moves(event_id TEXT,product_id TEXT,quantity INTEGER,value_delta INTEGER);
CREATE TABLE journal(event_id TEXT,account TEXT,currency TEXT,amount INTEGER);""")
db.executemany('INSERT INTO events VALUES(?,?,?,?,?)',[('e1','2026-10-20','STOCKTAKE',1,'count'),('e2','2026-10-21','STOCKTAKE',2,'count'),('sale','2026-10-21','SALE',3,'sale')])
db.executemany('INSERT INTO products VALUES(?,?,?)',[('a','watch A','USD'),('b','watch B','USD'),('c','watch C','TRY')])
db.executemany('INSERT INTO stock_moves VALUES(?,?,?,?)',[('e1','a',-1,-120),('e1','b',2,200),('e1','c',1,500),('e2','a',-1,-120),('sale','a',-1,-120)])
db.executemany('INSERT INTO journal VALUES(?,?,?,?)',[('e1','inventory','USD',80),('e1','inventory','TRY',500),('e2','inventory','USD',-120),('sale','inventory','USD',-120)])
def reconciles():
    rows=db.execute("""SELECT e.id,p.currency,s.value_delta FROM events e JOIN stock_moves s ON s.event_id=e.id JOIN products p ON p.id=s.product_id WHERE e.kind='STOCKTAKE' AND e.date>=? AND e.date<=?""",('2026-10-01','2026-10-31')).fetchall()
    groups={}
    for event,currency,delta in rows:
        groups[(event,currency)]=groups.get((event,currency),0)+delta
    return all(db.execute("SELECT coalesce(sum(amount),0) FROM journal WHERE event_id=? AND account='inventory' AND currency=?",key).fetchone()[0]==value for key,value in groups.items())
assert reconciles()
db.execute("UPDATE journal SET amount=79 WHERE event_id='e1' AND currency='USD'")
assert not reconciles()
db.execute("UPDATE journal SET amount=80 WHERE event_id='e1' AND currency='USD'")
assert reconciles()
print('PASS stocktake: multiple products, currencies, events, and mismatch detection')
