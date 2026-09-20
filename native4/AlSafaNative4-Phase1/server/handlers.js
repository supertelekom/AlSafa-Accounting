'use strict';

function testStoreOnly(command) {
  if (!command.storeId.startsWith('test-')) {
    const error = new Error('معالج المنتج غير متاح إلا في مخزن الاختبار');
    error.code = 'TEST_STORE_REQUIRED';
    throw error;
  }
}

function productDocument(command) {
  testStoreOnly(command);
  const payload = command.payload;
  return {
    id: command.recordId,
    name: String(payload.name).trim(),
    barcode: payload.barcode == null ? null : String(payload.barcode).trim() || null,
    quantity: payload.initialQuantity,
    price: payload.price,
    createdFromOperationId: command.operationId,
    sourceDeviceId: command.sourceDeviceId
  };
}

module.exports = { productDocument };
