'use strict';

const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const { validateCommand } = require('./protocol');
const { productDocument } = require('./handlers');

initializeApp();
const db = getFirestore();

function asHttpsError(error) {
  if (error instanceof HttpsError) return error;
  const invalid = ['INVALID_COMMAND', 'INVALID_OPERATION_ID', 'MISSING_REFERENCE', 'UNSUPPORTED_KIND', 'INVALID_PAYLOAD', 'INVALID_TEST_PAYLOAD', 'INVALID_PRODUCT_PAYLOAD'];
  if (error.code === 'PRODUCT_EXISTS') return new HttpsError('already-exists', error.message);
  if (error.code === 'TEST_STORE_REQUIRED') return new HttpsError('failed-precondition', error.message);
  return new HttpsError(invalid.includes(error.code) ? 'invalid-argument' : 'internal', error.message || 'تعذر معالجة الأمر');
}

exports.submitCommand = onCall({ region: 'europe-west1' }, async request => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'يجب تسجيل الدخول قبل إرسال عملية');
  let command;
  try { command = validateCommand(request.data); } catch (error) { throw asHttpsError(error); }

  const store = db.collection('native4Stores').doc(command.storeId);
  const receipt = store.collection('commandReceipts').doc(command.operationId);
  const meta = store.collection('_meta').doc('current');
  return db.runTransaction(async transaction => {
    const previous = await transaction.get(receipt);
    if (previous.exists) return { status: 'CONFIRMED', duplicate: true, revision: previous.data().revision };

    let productRef = null;
    let product = null;
    if (command.kind === 'PRODUCT_CREATE') {
      product = productDocument(command);
      productRef = store.collection('products').doc(command.recordId);
      const existingProduct = await transaction.get(productRef);
      if (existingProduct.exists) {
        const error = new Error('معرف المنتج مستخدم بالفعل ولا يجوز الاستبدال');
        error.code = 'PRODUCT_EXISTS';
        throw error;
      }
    } else if (command.kind !== 'TEST') {
      throw new HttpsError('failed-precondition', 'هذه العملية لم تُفعّل بعد في خادم Native 4');
    }

    const metaSnapshot = await transaction.get(meta);
    const revision = Number(metaSnapshot.exists ? metaSnapshot.data().revision || 0 : 0) + 1;
    const event = store.collection('changeLog').doc(String(revision).padStart(12, '0'));
    const result = { status: 'CONFIRMED', duplicate: false, revision };
    if (productRef) transaction.set(productRef, { ...product, createdAt: FieldValue.serverTimestamp() });
    transaction.set(receipt, { ...command, revision, result, acceptedAt: FieldValue.serverTimestamp(), acceptedByUid: request.auth.uid });
    transaction.set(event, { operationId: command.operationId, kind: command.kind, recordId: command.recordId, sourceDeviceId: command.sourceDeviceId, revision, at: FieldValue.serverTimestamp() });
    transaction.set(meta, { revision, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return result;
  });
});
