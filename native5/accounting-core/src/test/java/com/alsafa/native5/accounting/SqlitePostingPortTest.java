package com.alsafa.native5.accounting;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;
import static com.alsafa.native5.accounting.Money.Currency.USD;

public class SqlitePostingPortTest {
    private static PostingBatch sale(String id, long quantity) {
        JournalEntry entry = new JournalEntry(id, Arrays.asList(
            new JournalEntry.Line("cash",new Money(USD,1800)),
            new JournalEntry.Line("sales",new Money(USD,-1800))));
        return new PostingBatch(entry,"SALE","{\"eventId\":\""+id+"\"}",
            Collections.singletonList(new InventoryDelta("watch-1",quantity)));
    }
    private static long count(Connection c,String table) throws Exception {
        try (Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)) {
            r.next(); return r.getLong(1);
        }
    }
    private static Connection database() throws Exception {
        Connection c=DriverManager.getConnection("jdbc:sqlite::memory:");
        Path schema=Path.of("..","schema","001_initial.sql");
        String sql=Files.readString(schema);
        try (Statement s=c.createStatement()) {
            for(String statement:sql.split(";")) {
                String cleaned=statement.replaceAll("(?m)^--[^\\n]*$","").trim();
                if(!cleaned.isEmpty())s.execute(cleaned);
            }
            s.execute("INSERT INTO inventory_balances(product_id,quantity) VALUES('watch-1',2)");
        }
        return c;
    }
    @Test public void successAndDuplicateProtection() throws Exception {
        try(Connection c=database()) {
            AtomicPostingService service=new AtomicPostingService(new SqlitePostingPort(c));
            service.post(sale("sale-1",-1));
            assertEquals(1,count(c,"accounting_events"));
            assertEquals(2,count(c,"journal_lines"));
            assertEquals(1,count(c,"inventory_movements"));
            assertEquals(1,count(c,"outbox"));
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT quantity FROM inventory_balances WHERE product_id='watch-1'")){
                assertTrue(r.next()); assertEquals(1,r.getLong(1));
            }
            try { service.post(sale("sale-1",-1));fail("Duplicate must fail"); }
            catch(IllegalStateException expected) {}
            assertEquals(1,count(c,"outbox"));
        }
    }
    @Test public void insufficientStockRollsBackEveryTable() throws Exception {
        try(Connection c=database()) {
            AtomicPostingService service=new AtomicPostingService(new SqlitePostingPort(c));
            try { service.post(sale("sale-2",-3));fail("Insufficient stock must fail"); }
            catch(IllegalStateException expected) {}
            assertEquals(0,count(c,"accounting_events"));
            assertEquals(0,count(c,"journal_lines"));
            assertEquals(0,count(c,"inventory_movements"));
            assertEquals(0,count(c,"outbox"));
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT quantity FROM inventory_balances WHERE product_id='watch-1'")){
                assertTrue(r.next()); assertEquals(2,r.getLong(1));
            }
        }
    }
    @Test public void missingProductRollsBackEntirePosting() throws Exception {
        try(Connection c=database()) {
            JournalEntry entry = new JournalEntry("sale-missing", Arrays.asList(
                new JournalEntry.Line("cash",new Money(USD,1800)),
                new JournalEntry.Line("sales",new Money(USD,-1800))));
            PostingBatch batch = new PostingBatch(entry,"SALE","{\"eventId\":\"sale-missing\"}",
                Collections.singletonList(new InventoryDelta("unknown-watch",-1)));
            try { new AtomicPostingService(new SqlitePostingPort(c)).post(batch); fail("Missing product must fail"); }
            catch(IllegalStateException expected) { }
            assertEquals(0,count(c,"accounting_events"));
            assertEquals(0,count(c,"outbox"));
        }
    }
    @Test public void successfulSaleFollowedByFailedSalePreservesFirstSale() throws Exception {
        try(Connection c=database()) {
            AtomicPostingService service = new AtomicPostingService(new SqlitePostingPort(c));
            service.post(sale("sale-ok",-1));
            try { service.post(sale("sale-failed",-2)); fail("Must reject insufficient stock"); }
            catch(IllegalStateException expected) { }
            assertEquals(1,count(c,"accounting_events"));
            assertEquals(1,count(c,"outbox"));
            try(Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT quantity FROM inventory_balances WHERE product_id='watch-1'")) {
                assertTrue(rs.next()); assertEquals(1,rs.getLong(1));
            }
        }
    }
}
