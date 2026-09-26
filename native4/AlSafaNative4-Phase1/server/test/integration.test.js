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
  const db = getFirestore();
  const storeId = 'test-native4-integration';
  const store = db.collection('native4Stores').doc(storeId);
  const uuid = n => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
  const sale = (n, recordId, productId, quantity, paymentMethod = 'CASH') => ({ operationId: uuid(n), storeId, sourceDeviceId: 'phone-test', kind: 'SALE', recordId, payload: { items: [{ productId, quantity, unitPrice: 10 }], currency: 'USD', paymentMethod, total: quantity * 10 } });

  async function clearStore() {
    for (const name of ['products','sales','inventoryMovements','cashMovements','auditLog','commandReceipts','changeLog','_meta']) {
      const snap = await store.collection(name).get();
      if (!snap.empty) { const batch = db.batch(); snap.docs.forEach(d => batch.delete(d.ref)); await batch.commit(); }
    }
  }
  async function seed(id, quantity) { await store.collection('products').doc(id).set({ id, name: id, quantity, price: 10 }); }

  test.beforeEach(clearStore);

  test('SALE commits sale, stock, cash, audit, receipt and revision exactly once', async () => {
    await seed('p1', 3);
    const cmd = sale(1, 's1', 'p1', 1);
    const first = await processCommand(db, cmd, 'tester');
    const second = await processCommand(db, cmd, 'tester');
    assert.equal(first.duplicate, false); assert.equal(second.duplicate, true); assert.equal(second.revision, first.revision);
    assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 2);
    assert.equal((await store.collection('sales').get()).size, 1);
    assert.equal((await store.collection('inventoryMovements').get()).size, 1);
    assert.equal((await store.collection('cashMovements').get()).size, 1);
    assert.equal((await store.collection('auditLog').get()).size, 1);
    assert.equal((await store.collection('commandReceipts').get()).size, 1);
    assert.equal((await store.collection('changeLog').get()).size, 1);
  });

  test('insufficient stock rolls back every sale-side write', async () => {
    await seed('p1', 1);
    await assert.rejects(() => processCommand(db, sale(2, 's2', 'p1', 2), 'tester'), e => e.code === 'INSUFFICIENT_STOCK');
    assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 1);
    for (const name of ['sales','inventoryMovements','cashMovements','auditLog','commandReceipts','changeLog']) assert.equal((await store.collection(name).get()).size, 0);
  });

  test('missing product rolls back completely', async () => {
    await assert.rejects(() => processCommand(db, sale(3, 's3', 'missing', 1), 'tester'), e => e.code === 'PRODUCT_NOT_FOUND');
    for (const name of ['sales','inventoryMovements','cashMovements','auditLog','commandReceipts','changeLog']) assert.equal((await store.collection(name).get()).size, 0);
  });

  test('LATER sale changes stock but does not create cash movement', async () => {
    await seed('p1', 2);
    await processCommand(db, sale(4, 's4', 'p1', 1, 'LATER'), 'tester');
    assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 1);
    assert.equal((await store.collection('sales').get()).size, 1);
    assert.equal((await store.collection('cashMovements').get()).size, 0);
  });

  test('two concurrent sales cannot both consume the final item', async () => {
    await seed('p1', 1);
    const results = await Promise.allSettled([processCommand(db, sale(5, 's5a', 'p1', 1), 'tester-a'), processCommand(db, sale(6, 's5b', 'p1', 1), 'tester-b')]);
    assert.equal(results.filter(r => r.status === 'fulfilled').length, 1);
    assert.equal(results.filter(r => r.status === 'rejected').length, 1);
    assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 0);
    assert.equal((await store.collection('sales').get()).size, 1);
    assert.equal((await store.collection('cashMovements').get()).size, 1);
  });
}
