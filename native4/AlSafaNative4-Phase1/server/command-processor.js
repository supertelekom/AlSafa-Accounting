'use strict';

const { FieldValue } = require('firebase-admin/firestore');
const { validateCommand } = require('./protocol');
const { productDocument, saleDocument, testStoreOnly } = require('./handlers');

async function processCommand(db, rawCommand, actorUid) {
  const command = validateCommand(rawCommand);
  if (!actorUid) { const error = new Error('يجب تسجيل الدخول قبل إرسال عملية'); error.code = 'UNAUTHENTICATED'; throw error; }
  const store = db.collection('native4Stores').doc(command.storeId);
  const receipt = store.collection('commandReceipts').doc(command.operationId);
  const meta = store.collection('_meta').doc('current');

  return db.runTransaction(async tx => {
    const previous = await tx.get(receipt);
    if (previous.exists) return { status: 'CONFIRMED', duplicate: true, revision: previous.data().revision };
    testStoreOnly(command);
    let primaryRef = null;
    let primaryData = null;
    let primaryMerge = false;
    const writes = [];

    if (command.kind === 'PRODUCT_CREATE') {
      primaryData = productDocument(command);
      primaryRef = store.collection('products').doc(command.recordId);
      if ((await tx.get(primaryRef)).exists) { const error = new Error('معرف المنتج مستخدم بالفعل'); error.code = 'PRODUCT_EXISTS'; throw error; }
    } else if (command.kind === 'PRODUCT_UPDATE') {
      primaryRef = store.collection('products').doc(command.recordId);
      if (!(await tx.get(primaryRef)).exists) { const error = new Error('المنتج غير موجود'); error.code = 'PRODUCT_NOT_FOUND'; throw error; }
      primaryData = { ...command.payload, updatedAt: FieldValue.serverTimestamp() };
      primaryMerge = true;
      writes.push([store.collection('auditLog').doc(command.operationId), { operationId: command.operationId, kind: 'PRODUCT_UPDATE', recordId: command.recordId, sourceDeviceId: command.sourceDeviceId, actorUid, at: FieldValue.serverTimestamp() }, false]);
    } else if (command.kind === 'SALE') {
      primaryData = saleDocument(command);
      primaryRef = store.collection('sales').doc(command.recordId);
      if ((await tx.get(primaryRef)).exists) { const error = new Error('معرف فاتورة البيع مستخدم بالفعل'); error.code = 'SALE_EXISTS'; throw error; }
      for (const item of primaryData.items) {
        const productRef = store.collection('products').doc(item.productId);
        const snap = await tx.get(productRef);
        if (!snap.exists) { const error = new Error('المنتج غير موجود'); error.code = 'PRODUCT_NOT_FOUND'; throw error; }
        const quantity = Number(snap.data().quantity || 0);
        if (!Number.isFinite(quantity) || quantity < item.quantity) { const error = new Error('المخزون غير كافٍ'); error.code = 'INSUFFICIENT_STOCK'; throw error; }
        writes.push([productRef, { quantity: quantity - item.quantity, updatedAt: FieldValue.serverTimestamp() }, true]);
        writes.push([store.collection('inventoryMovements').doc(`${command.operationId}-${item.productId}`), { operationId: command.operationId, saleId: command.recordId, productId: item.productId, quantityDelta: -item.quantity, sourceDeviceId: command.sourceDeviceId, at: FieldValue.serverTimestamp() }, false]);
      }
      if (primaryData.paymentMethod !== 'LATER') writes.push([store.collection('cashMovements').doc(command.operationId), { operationId: command.operationId, saleId: command.recordId, direction: 'IN', amount: primaryData.total, currency: primaryData.currency, paymentMethod: primaryData.paymentMethod, sourceDeviceId: command.sourceDeviceId, at: FieldValue.serverTimestamp() }, false]);
      writes.push([store.collection('auditLog').doc(command.operationId), { operationId: command.operationId, kind: 'SALE', recordId: command.recordId, sourceDeviceId: command.sourceDeviceId, actorUid, at: FieldValue.serverTimestamp() }, false]);
    } else if (command.kind !== 'TEST') {
      const error = new Error('هذه العملية لم تُفعّل بعد'); error.code = 'NOT_ENABLED'; throw error;
    }

    const metaSnap = await tx.get(meta);
    const revision = Number(metaSnap.exists ? metaSnap.data().revision || 0 : 0) + 1;
    if (primaryRef) tx.set(primaryRef, primaryMerge ? primaryData : { ...primaryData, createdAt: FieldValue.serverTimestamp() }, primaryMerge ? { merge: true } : undefined);
    for (const [ref, data, merge] of writes) tx.set(ref, data, merge ? { merge: true } : undefined);
    tx.set(receipt, { ...command, revision, acceptedAt: FieldValue.serverTimestamp(), acceptedByUid: actorUid });
    tx.set(store.collection('changeLog').doc(String(revision).padStart(12, '0')), { operationId: command.operationId, kind: command.kind, recordId: command.recordId, sourceDeviceId: command.sourceDeviceId, revision, at: FieldValue.serverTimestamp() });
    tx.set(meta, { revision, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return { status: 'CONFIRMED', duplicate: false, revision };
  });
}

module.exports = { processCommand };
