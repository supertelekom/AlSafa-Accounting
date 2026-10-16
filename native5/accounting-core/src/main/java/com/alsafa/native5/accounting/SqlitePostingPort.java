package com.alsafa.native5.accounting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * JDBC SQLite reference implementation. Uses a single connection; caller owns connection
 * lifetime. Android SQLiteDatabase adapter will use the same schema and posting contract.
 */
public final class SqlitePostingPort implements AtomicPostingService.TransactionPort {
    private final Connection connection;
    private boolean active;
    public SqlitePostingPort(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }
    @Override public void begin() {
        if (active) throw new IllegalStateException("Transaction already active");
        try {
            if (!connection.getAutoCommit()) throw new IllegalStateException("Connection already in transaction");
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
            }
            connection.setAutoCommit(false);
            active = true;
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public boolean eventExists(String id) {
        requireActive();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM accounting_events WHERE event_id=?")) {
            statement.setString(1,id);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public void insertEvent(String id,String kind) {
        requireActive();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO accounting_events(event_id,operation_kind,created_at_ms) VALUES(?,?,?)")) {
            statement.setString(1,id); statement.setString(2,kind);
            statement.setLong(3,System.currentTimeMillis()); statement.executeUpdate();
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public void insertJournalLine(String id,int lineNo,JournalEntry.Line line) {
        requireActive();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO journal_lines(event_id,line_no,account_id,currency,signed_minor_units) VALUES(?,?,?,?,?)")) {
            statement.setString(1,id); statement.setInt(2,lineNo); statement.setString(3,line.accountId);
            statement.setString(4,line.amount.currency().name());
            statement.setLong(5,line.amount.minorUnits()); statement.executeUpdate();
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public void applyInventory(String id,int lineNo,InventoryDelta delta) {
        requireActive();
        long change = delta.quantityChange();
        if (change == Long.MIN_VALUE) throw new IllegalArgumentException("Inventory delta exceeds supported range");
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE inventory_balances SET quantity=quantity+? WHERE product_id=? AND quantity>=? AND quantity<=?")) {
            update.setLong(1,change);
            update.setString(2,delta.productId());
            update.setLong(3,change<0 ? -change : 0);
            update.setLong(4,change>0 ? Long.MAX_VALUE-change : Long.MAX_VALUE);
            if (update.executeUpdate()!=1) throw new IllegalStateException("Insufficient stock or missing product: "+delta.productId());
        } catch (SQLException e) { throw failure(e); }
        try (PreparedStatement movement = connection.prepareStatement(
                "INSERT INTO inventory_movements(event_id,line_no,product_id,quantity_delta) VALUES(?,?,?,?)")) {
            movement.setString(1,id); movement.setInt(2,lineNo);
            movement.setString(3,delta.productId()); movement.setLong(4,delta.quantityChange());
            movement.executeUpdate();
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public void insertOutbox(String id,String kind,String payload) {
        requireActive();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO outbox(operation_id,operation_kind,payload,created_at_ms) VALUES(?,?,?,?)")) {
            statement.setString(1,id); statement.setString(2,kind); statement.setString(3,payload);
            statement.setLong(4,System.currentTimeMillis()); statement.executeUpdate();
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public void commit() {
        requireActive();
        try { connection.commit(); connection.setAutoCommit(true); active=false; }
        catch (SQLException e) { throw failure(e); }
    }
    @Override public void rollback() {
        if (!active) return;
        try { connection.rollback(); }
        catch (SQLException e) { throw failure(e); }
        finally {
            try { connection.setAutoCommit(true); }
            catch (SQLException e) { throw failure(e); }
            active=false;
        }
    }
    private void requireActive() {
        if (!active) throw new IllegalStateException("Posting transaction not started");
    }
    private static IllegalStateException failure(SQLException e) {
        return new IllegalStateException("SQLite posting failed: "+e.getMessage(),e);
    }
}
