'use strict';

function testStoreOnly(command) {
  if (!command.storeId.startsWith('test-') && !command.storeId.endsWith('-test')) {
    const error = new Error('معالجات Native 4 الحالية غير متاحة إلا في مخزن الاختبار');
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

function saleDocument(command) {
  testStoreOnly(command);
  const payload = command.payload;
  return {
    id: command.recordId,
    items: payload.items.map(item => ({ productId: String(item.productId).trim(), quantity: item.quantity, unitPrice: item.unitPrice })),
    currency: payload.currency,
    paymentMethod: payload.paymentMethod,
    total: payload.total,
    customerId: payload.customerId == null ? null : String(payload.customerId).trim() || null,
    createdFromOperationId: command.operationId,
    sourceDeviceId: command.sourceDeviceId
  };
}

module.exports = { productDocument, saleDocument, testStoreOnly };
