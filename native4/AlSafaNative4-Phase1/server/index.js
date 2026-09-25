'use strict';

const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const { validateCommand } = require('./protocol');
const { productDocument, saleDocument, testStoreOnly } = require('./handlers');

initializeApp();
const db = getFirestore();

function asHttpsError(error) {
  if (error instanceof HttpsError) return error;
  const invalid = ['INVALID_COMMAND', 'INVALID_OPERATION_ID', 'MISSING_REFERENCE', 'UNSUPPORTED_KIND', 'INVALID_PAYLOAD', 'INVALID_TEST_PAYLOAD', 'INVALID_PRODUCT_PAYLOAD', 'INVALID_SALE_PAYLOAD'];
  if (error.code === 'PRODUCT_EXISTS' || error.code === 'SALE_EXISTS') return new HttpsError('already-exists', error.message);
  if (error.code === 'INSUFFICIENT_STOCK' || error.code === 'PRODUCT_NOT_FOUND' || error.code === 'TEST_STORE_REQUIRED') return new HttpsError('failed-precondition', error.message);
  return new HttpsError(invalid.includes(error.code) ? 'invalid-argument' : 'internal', error.message || 'تعذر معالجة الأمر');
}

exports.submitCommand = onCall({ region: 'europe-west1' }, async request => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'يجب تسجيل الدخول قبل إرسال عملية');
  let command;
  try { command = validateCommand(request.data); } catch (error) { throw asHttpsError(error); }

  const store = db.collection('native4Stores').doc(command.storeId);
  const receipt = store.collection('commandReceipts').doc(command.operationId);
  const meta = store.collection('_meta').doc('current');

  try {
    return await db.runTransaction(async transaction => {
      const previous = await transaction.get(receipt);
      if (previous.exists) return { status: 'CONFIRMED', duplicate: true, revision: previous.data().revision };

      testStoreOnly(command);
      let primaryRef = null;
      let primaryData = null;
      const extraWrites = [];

      if (command.kind === 'PRODUCT_CREATE') {
        primaryData = productDocument(command);
        primaryRef = store.collection('products').doc(command.recordId);
        const existingProduct = await transaction.get(primaryRef);
        if (existingProduct.exists) { const error = new Error('معرف المنتج مستخدم بالفعل ولا يجوز الاستبدال'); error.code = 'PRODUCT_EXISTS'; throw error; }
      } else if (command.kind === 'SALE') {
        primaryData = saleDocument(command);
        primaryRef = store.collection('sales').doc(command.recordId);
        const saleSnapshot = await transaction.get(primaryRef);
        if (saleSnapshot.exists) { const error = new Error('معرف فاتورة البيع مستخدم بالفعل'); error.code = 'SALE_EXISTS'; throw error; }

        for (const item of primaryData.items) {
          const productRef = store.collection('products').doc(item.productId);
          const productSnapshot = await transaction.get(productRef);
          if (!productSnapshot.exists) { const error = new Error(`المنتج غير موجود: ${item.productId}`); error.code = 'PRODUCT_NOT_FOUND'; throw error; }
          const currentQuantity = Number(productSnapshot.data().quantity || 0);
          if (!Number.isFinite(currentQuantity) || currentQuantity < item.quantity) { const error = new Error(`المخزون غير كافٍ للمنتج: ${item.productId}`); error.code = 'INSUFFICIENT_STOCK'; throw error; }
          extraWrites.push({ ref: productRef, data: { quantity: currentQuantity - item.quantity, updatedAt: FieldValue.serverTimestamp() }, merge: true });
          const movementRef = store.collection('inventoryMovements').doc(`${command.operationId}-${item.productId}`);
          extraWrites.push({ ref: movementRef, data: { operationId: command.operationId, saleId: command.recordId, productId: item.productId, quantityDelta: -item.quantity, sourceDeviceId: command.sourceDeviceId, at: FieldValue.serverTimestamp() } });
        }
        if (primaryData.paymentMethod !== 'LATER') {
          const cashRef = store.collection('cashMovements').doc(command.operationId);
          extraWrites.push({ ref: cashRef, data: { operationId: command.operationId, saleId: command.recordId, direction: 'IN', amount: primaryData.total, currency: primaryData.currency, paymentMethod: primaryData.paymentMethod, sourceDeviceId: command.sourceDeviceId, at: FieldValue.serverTimestamp() } });
        }
        const auditRef = store.collection('auditLog').doc(command.operationId);
        extraWrites.push({ ref: auditRef, data: { operationId: command.operationId, kind: 'SALE', recordId: command.recordId, sourceDeviceId: command.sourceDeviceId, actorUid: request.auth.uid, at: FieldValue.serverTimestamp() } });
      } else if (command.kind !== 'TEST') {
        throw new HttpsError('failed-precondition', 'هذه العملية لم تُفعّل بعد في خادم Native 4');
      }

      const metaSnapshot = await transaction.get(meta);
      const revision = Number(metaSnapshot.exists ? metaSnapshot.data().revision || 0 : 0) + 1;
      const event = store.collection('changeLog').doc(String(revision).padStart(12, '0'));
      const result = { status: 'CONFIRMED', duplicate: false, revision };
      if (primaryRef) transaction.set(primaryRef, { ...primaryData, createdAt: FieldValue.serverTimestamp() });
      for (const write of extraWrites) transaction.set(write.ref, write.data, write.merge ? { merge: true } : undefined);
      transaction.set(receipt, { ...command, revision, result, acceptedAt: FieldValue.serverTimestamp(), acceptedByUid: request.auth.uid });
      transaction.set(event, { operationId: command.operationId, kind: command.kind, recordId: command.recordId, sourceDeviceId: command.sourceDeviceId, revision, at: FieldValue.serverTimestamp() });
      transaction.set(meta, { revision, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      return result;
    });
  } catch (error) { throw asHttpsError(error); }
});
