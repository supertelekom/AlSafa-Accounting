'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { initializeApp, getApps } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { processCommand } = require('../command-processor');

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  test('integration tests require Firestore Emulator', { skip: true }, () => {});
} else {
  if (!getApps().length) initializeApp({ projectId: 'alsafa-native4-test' });
  const db = getFirestore(); const storeId = 'test-native4-integration'; const store = db.collection('native4Stores').doc(storeId);
  const uuid = n => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
  const base = (n, kind, recordId, payload) => ({ operationId: uuid(n), storeId, sourceDeviceId: 'phone-test', kind, recordId, payload });
  const sale = (n, recordId, productId, quantity, paymentMethod = 'CASH') => base(n, 'SALE', recordId, { items: [{ productId, quantity, unitPrice: 10 }], currency: 'USD', paymentMethod, total: quantity * 10 });
  const productUpdate = (n, recordId, payload) => base(n, 'PRODUCT_UPDATE', recordId, payload);
  const inventoryAdjustment = (n, recordId, quantityDelta, reason = 'manual count correction') => base(n, 'INVENTORY_ADJUSTMENT', recordId, { quantityDelta, reason });
  const cashTransfer = (n, recordId, fromAccount = 'DRAWER', toAccount = 'EXTERNAL', amount = 20, currency = 'USD') => base(n, 'CASH_MOVEMENT', recordId, { fromAccount, toAccount, amount, currency, reason: 'safe transfer' });
  const expense = (n, recordId, account = 'DRAWER', amount = 10, currency = 'USD') => base(n, 'EXPENSE', recordId, { account, amount, currency, reason: 'shop expense' });

  async function clearStore() {
    for (const name of ['products','sales','inventoryMovements','cashMovements','cashTransfers','expenses','auditLog','commandReceipts','changeLog','_meta']) {
      const snap = await store.collection(name).get(); if (!snap.empty) { const batch = db.batch(); snap.docs.forEach(d => batch.delete(d.ref)); await batch.commit(); }
    }
  }
  async function seed(id, quantity) { await store.collection('products').doc(id).set({ id, name: id, quantity, price: 10 }); }
  test.beforeEach(clearStore);

  test('SALE commits sale, stock, cash, audit, receipt and revision exactly once', async () => {
    await seed('p1', 3); const cmd = sale(1, 's1', 'p1', 1); const first = await processCommand(db, cmd, 'tester'); const second = await processCommand(db, cmd, 'tester');
    assert.equal(first.duplicate, false); assert.equal(second.duplicate, true); assert.equal(second.revision, first.revision);
    assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 2); assert.equal((await store.collection('sales').get()).size, 1); assert.equal((await store.collection('inventoryMovements').get()).size, 1); assert.equal((await store.collection('cashMovements').get()).size, 1); assert.equal((await store.collection('auditLog').get()).size, 1); assert.equal((await store.collection('commandReceipts').get()).size, 1); assert.equal((await store.collection('changeLog').get()).size, 1);
  });

  test('PRODUCT_UPDATE changes metadata but preserves stock and is idempotent', async () => {
    await seed('p1', 7); const cmd = productUpdate(20, 'p1', { name: 'updated', price: 12 }); const first = await processCommand(db, cmd, 'tester'); const second = await processCommand(db, cmd, 'tester'); const data = (await store.collection('products').doc('p1').get()).data();
    assert.equal(first.duplicate, false); assert.equal(second.duplicate, true); assert.equal(data.quantity, 7); assert.equal(data.name, 'updated'); assert.equal(data.price, 12); assert.equal((await store.collection('auditLog').get()).size, 1); assert.equal((await store.collection('changeLog').get()).size, 1);
  });

  test('PRODUCT_UPDATE cannot smuggle a stock overwrite', async () => {
    await seed('p1', 7); await assert.rejects(() => processCommand(db, productUpdate(21, 'p1', { quantity: 999 }), 'tester'), e => e.code === 'INVALID_PRODUCT_UPDATE'); assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 7); assert.equal((await store.collection('commandReceipts').get()).size, 0); assert.equal((await store.collection('changeLog').get()).size, 0);
  });

  test('PRODUCT_UPDATE missing product rolls back completely', async () => {
    await assert.rejects(() => processCommand(db, productUpdate(22, 'missing', { price: 12 }), 'tester'), e => e.code === 'PRODUCT_NOT_FOUND'); assert.equal((await store.collection('auditLog').get()).size, 0); assert.equal((await store.collection('commandReceipts').get()).size, 0); assert.equal((await store.collection('changeLog').get()).size, 0);
  });

  test('INVENTORY_ADJUSTMENT changes stock exactly once and records before/after', async () => {
    await seed('p1', 7); const cmd = inventoryAdjustment(30, 'p1', 3, 'opening count correction'); const first = await processCommand(db, cmd, 'tester'); const second = await processCommand(db, cmd, 'tester');
    assert.equal(first.duplicate, false); assert.equal(second.duplicate, true); assert.equal(second.revision, first.revision); assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 10);
    const movement = (await store.collection('inventoryMovements').doc(cmd.operationId).get()).data(); assert.equal(movement.quantityBefore, 7); assert.equal(movement.quantityDelta, 3); assert.equal(movement.quantityAfter, 10); assert.equal((await store.collection('inventoryMovements').get()).size, 1); assert.equal((await store.collection('auditLog').get()).size, 1); assert.equal((await store.collection('commandReceipts').get()).size, 1); assert.equal((await store.collection('changeLog').get()).size, 1);
  });

  test('INVENTORY_ADJUSTMENT refuses negative resulting stock and rolls back', async () => {
    await seed('p1', 2); await assert.rejects(() => processCommand(db, inventoryAdjustment(31, 'p1', -3), 'tester'), e => e.code === 'INSUFFICIENT_STOCK'); assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 2); for (const name of ['inventoryMovements','auditLog','commandReceipts','changeLog']) assert.equal((await store.collection(name).get()).size, 0);
  });

  test('INVENTORY_ADJUSTMENT missing product rolls back completely', async () => {
    await assert.rejects(() => processCommand(db, inventoryAdjustment(32, 'missing', 1), 'tester'), e => e.code === 'PRODUCT_NOT_FOUND'); for (const name of ['inventoryMovements','auditLog','commandReceipts','changeLog']) assert.equal((await store.collection(name).get()).size, 0);
  });

  test('concurrent inventory decrements cannot push stock below zero', async () => {
    await seed('p1', 1); const results = await Promise.allSettled([processCommand(db, inventoryAdjustment(33, 'p1', -1, 'count a'), 'tester-a'), processCommand(db, inventoryAdjustment(34, 'p1', -1, 'count b'), 'tester-b')]); assert.equal(results.filter(r => r.status === 'fulfilled').length, 1); assert.equal(results.filter(r => r.status === 'rejected').length, 1); assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 0); assert.equal((await store.collection('inventoryMovements').get()).size, 1);
  });

  test('CASH_MOVEMENT creates balanced OUT and IN legs exactly once', async () => {
    const cmd = cashTransfer(40, 't1'); const first = await processCommand(db, cmd, 'tester'); const second = await processCommand(db, cmd, 'tester'); assert.equal(first.duplicate, false); assert.equal(second.duplicate, true);
    const moves = await store.collection('cashMovements').get(); assert.equal(moves.size, 2); const data = moves.docs.map(d => d.data()); assert.equal(data.filter(x => x.direction === 'OUT' && x.account === 'DRAWER').length, 1); assert.equal(data.filter(x => x.direction === 'IN' && x.account === 'EXTERNAL').length, 1); assert.equal(data.reduce((n, x) => n + (x.direction === 'IN' ? x.amount : -x.amount), 0), 0); assert.equal((await store.collection('cashTransfers').get()).size, 1); assert.equal((await store.collection('auditLog').get()).size, 1); assert.equal((await store.collection('changeLog').get()).size, 1);
  });

  test('EXPENSE creates one OUT cash leg and is idempotent', async () => {
    const cmd = expense(41, 'e1', 'DRAWER', 15, 'TRY'); const first = await processCommand(db, cmd, 'tester'); const second = await processCommand(db, cmd, 'tester'); assert.equal(first.duplicate, false); assert.equal(second.duplicate, true); assert.equal((await store.collection('expenses').get()).size, 1); const moves = await store.collection('cashMovements').get(); assert.equal(moves.size, 1); const move = moves.docs[0].data(); assert.equal(move.direction, 'OUT'); assert.equal(move.account, 'DRAWER'); assert.equal(move.amount, 15); assert.equal(move.currency, 'TRY'); assert.equal((await store.collection('changeLog').get()).size, 1);
  });

  test('insufficient stock rolls back every sale-side write', async () => {
    await seed('p1', 1); await assert.rejects(() => processCommand(db, sale(2, 's2', 'p1', 2), 'tester'), e => e.code === 'INSUFFICIENT_STOCK'); assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 1); for (const name of ['sales','inventoryMovements','cashMovements','auditLog','commandReceipts','changeLog']) assert.equal((await store.collection(name).get()).size, 0);
  });

  test('missing product rolls back completely', async () => {
    await assert.rejects(() => processCommand(db, sale(3, 's3', 'missing', 1), 'tester'), e => e.code === 'PRODUCT_NOT_FOUND'); for (const name of ['sales','inventoryMovements','cashMovements','auditLog','commandReceipts','changeLog']) assert.equal((await store.collection(name).get()).size, 0);
  });

  test('LATER sale changes stock but does not create cash movement', async () => {
    await seed('p1', 2); await processCommand(db, sale(4, 's4', 'p1', 1, 'LATER'), 'tester'); assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 1); assert.equal((await store.collection('sales').get()).size, 1); assert.equal((await store.collection('cashMovements').get()).size, 0);
  });

  test('SHAM_CASH sale posts to SHAM_CASH account', async () => {
    await seed('p1', 2); await processCommand(db, sale(7, 's7', 'p1', 1, 'SHAM_CASH'), 'tester'); const move = (await store.collection('cashMovements').get()).docs[0].data(); assert.equal(move.account, 'SHAM_CASH'); assert.equal(move.direction, 'IN');
  });

  test('two concurrent sales cannot both consume the final item', async () => {
    await seed('p1', 1); const results = await Promise.allSettled([processCommand(db, sale(5, 's5a', 'p1', 1), 'tester-a'), processCommand(db, sale(6, 's5b', 'p1', 1), 'tester-b')]); assert.equal(results.filter(r => r.status === 'fulfilled').length, 1); assert.equal(results.filter(r => r.status === 'rejected').length, 1); assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 0); assert.equal((await store.collection('sales').get()).size, 1); assert.equal((await store.collection('cashMovements').get()).size, 1);
  });
}
