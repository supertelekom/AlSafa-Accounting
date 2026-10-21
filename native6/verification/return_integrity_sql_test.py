"""SQLite invariants for posted sale returns; run without Android SDK."""
import sqlite3

db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
db.executescript("""
CREATE TABLE events(id TEXT PRIMARY KEY);
CREATE TABLE invoices(id TEXT PRIMARY KEY REFERENCES events(id));
CREATE TABLE products(id TEXT PRIMARY KEY);
CREATE TABLE invoice_lines(invoice_id TEXT NOT NULL REFERENCES invoices(id),line INTEGER NOT NULL,
 product_id TEXT NOT NULL REFERENCES products(id),qty INTEGER NOT NULL CHECK(qty>0),
 net INTEGER NOT NULL CHECK(net>=0),cost INTEGER NOT NULL CHECK(cost>=0),
 PRIMARY KEY(invoice_id,line));
CREATE TABLE returns(event_id TEXT NOT NULL REFERENCES events(id),invoice_id TEXT NOT NULL REFERENCES invoices(id),
 line INTEGER NOT NULL,qty INTEGER NOT NULL CHECK(qty>0),credit INTEGER NOT NULL CHECK(credit>=0),
 cost INTEGER NOT NULL CHECK(cost>=0),PRIMARY KEY(event_id,line),
 FOREIGN KEY(invoice_id,line) REFERENCES invoice_lines(invoice_id,line));
CREATE TRIGGER returns_update BEFORE UPDATE ON returns BEGIN SELECT RAISE(ABORT,'Immutable accounting record'); END;
CREATE TRIGGER returns_delete BEFORE DELETE ON returns BEGIN SELECT RAISE(ABORT,'Immutable accounting record'); END;
""")
db.executemany("INSERT INTO events VALUES(?)",[("sale",),("return-1",),("return-2",)])
db.execute("INSERT INTO invoices VALUES('sale')")
db.execute("INSERT INTO products VALUES('watch')")
db.execute("INSERT INTO invoice_lines VALUES('sale',0,'watch',3,101,60)")
db.execute("INSERT INTO returns VALUES('return-1','sale',0,1,34,20)")
db.execute("INSERT INTO returns VALUES('return-2','sale',0,2,67,40)")
assert db.execute("SELECT sum(qty),sum(credit),sum(cost) FROM returns WHERE invoice_id='sale'").fetchone()==(3,101,60)

def rejects(sql):
    try:
        with db:
            db.execute(sql)
    except sqlite3.IntegrityError:
        return
    raise AssertionError("SQLite accepted invalid accounting mutation: "+sql)

rejects("UPDATE returns SET credit=0 WHERE event_id='return-1'")
rejects("DELETE FROM returns WHERE event_id='return-1'")
rejects("INSERT INTO returns VALUES('return-1','sale',0,1,34,20')")
rejects("INSERT INTO returns VALUES('return-2','sale',5,1,1,1')")
rejects("INSERT INTO returns VALUES('return-2','sale',1,1,1,1')")
rejects("INSERT INTO returns VALUES('return-2','sale',2,-1,1,1')")
assert db.execute("SELECT count(*) FROM returns").fetchone()[0]==2
print("PASS: SQLite return foreign keys, immutable history and partial totals")
