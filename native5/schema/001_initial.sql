-- AlSafa Native5 accounting schema v1.
-- Apply with SQLite foreign_keys=ON. All monetary values are signed INTEGER minor units.
-- The application MUST post journals, inventory changes, and outbox within BEGIN IMMEDIATE / COMMIT.
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS accounting_events (
  event_id TEXT PRIMARY KEY NOT NULL,
  operation_kind TEXT NOT NULL,
  created_at_ms INTEGER NOT NULL,
  CHECK(length(trim(event_id)) > 0),
  CHECK(length(trim(operation_kind)) > 0)
);
CREATE TABLE IF NOT EXISTS journal_lines (
  event_id TEXT NOT NULL REFERENCES accounting_events(event_id) ON DELETE RESTRICT,
  line_no INTEGER NOT NULL CHECK(line_no >= 0),
  account_id TEXT NOT NULL,
  currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')),
  signed_minor_units INTEGER NOT NULL CHECK(signed_minor_units <> 0),
  PRIMARY KEY(event_id, line_no)
);
CREATE TABLE IF NOT EXISTS inventory_balances (
  product_id TEXT PRIMARY KEY NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity >= 0)
);
CREATE TABLE IF NOT EXISTS inventory_movements (
  event_id TEXT NOT NULL REFERENCES accounting_events(event_id) ON DELETE RESTRICT,
  line_no INTEGER NOT NULL CHECK(line_no >= 0),
  product_id TEXT NOT NULL REFERENCES inventory_balances(product_id) ON DELETE RESTRICT,
  quantity_delta INTEGER NOT NULL CHECK(quantity_delta <> 0),
  PRIMARY KEY(event_id, line_no)
);
CREATE TABLE IF NOT EXISTS outbox (
  operation_id TEXT PRIMARY KEY NOT NULL REFERENCES accounting_events(event_id) ON DELETE RESTRICT,
  operation_kind TEXT NOT NULL,
  payload TEXT NOT NULL,
  state TEXT NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','SENDING','RETRY','CONFIRMED','FAILED')),
  attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts >= 0),
  created_at_ms INTEGER NOT NULL,
  last_attempt_at_ms INTEGER,
  confirmed_revision INTEGER CHECK(confirmed_revision IS NULL OR confirmed_revision > 0),
  last_error TEXT
);
CREATE INDEX IF NOT EXISTS idx_native5_outbox_queue ON outbox(state, created_at_ms, operation_id);
-- Journal balance is validated by JournalEntry before posting. Database-level validation
-- and idempotent insert behavior are required in the forthcoming Android posting adapter.
