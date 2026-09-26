'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { initializeApp, getApps } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { processCommand } = require('../command-processor');

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  test('lost-response retry requires Firestore Emulator', { skip: true }, () => {});
} else {
  if (!getApps().length) initializeApp({ projectId: 'alsafa-native4-test' });
  const db = getFirestore();
  const storeId = 'test-lost-response';
  const store = db.collection('native4Stores').doc(storeId);
  const operationId = '10000000-0000-4000-8000-000000000001';
  const command = { operationId, storeId, sourceDeviceId: 'phone-test', kind: 'SALE', recordId: 'sale-lost-response', payload: { items: [{ productId: 'p1', quantity: 1, unitPrice: 25 }], currency: 'USD', paymentMethod: 'CASH', total: 25 } };

  async function clear() {
    for (const name of ['products','sales','inventoryMovements','cashMovements','auditLog','commandReceipts','changeLog','_meta']) {
      const snap = await store.collection(name).get();
      if (!snap.empty) { const batch = db.batch(); snap.docs.forEach(d => batch.delete(d.ref)); await batch.commit(); }
    }
  }

  test('server success followed by lost client response is safe to retry with same operationId', async () => {
    await clear();
    await store.collection('products').doc('p1').set({ id: 'p1', name: 'watch', quantity: 2, price: 25 });

    // First call reaches the server and commits. The client is assumed to lose this response.
    const committedButLost = await processCommand(db, command, 'tester');
    assert.equal(committedButLost.duplicate, false);
    assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 1);

    // After timeout/restart the client resends the exact persisted command.
    const retry = await processCommand(db, command, 'tester');
    assert.equal(retry.duplicate, true);
    assert.equal(retry.revision, committedButLost.revision);

    // No accounting side effect may occur twice.
    assert.equal((await store.collection('products').doc('p1').get()).data().quantity, 1);
    assert.equal((await store.collection('sales').get()).size, 1);
    assert.equal((await store.collection('inventoryMovements').get()).size, 1);
    assert.equal((await store.collection('cashMovements').get()).size, 1);
    assert.equal((await store.collection('auditLog').get()).size, 1);
    assert.equal((await store.collection('commandReceipts').get()).size, 1);
    assert.equal((await store.collection('changeLog').get()).size, 1);
  });
}
