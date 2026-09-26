'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { productDocument, saleDocument, testStoreOnly } = require('../handlers');

const command = {
  operationId: '2b0b38b8-4dc1-4f4d-8e27-8a7b88c1ef14', storeId: 'test-native4-phone', sourceDeviceId: 'phone-1',
  kind: 'PRODUCT_CREATE', recordId: 'product-001', payload: { name: ' ساعة اختبار ', barcode: ' TEST-001 ', initialQuantity: 2, price: 25 }
};

test('builds a minimal immutable product document for an isolated Native4 store', () => {
  assert.deepEqual(productDocument(command), { id: 'product-001', name: 'ساعة اختبار', barcode: 'TEST-001', quantity: 2, price: 25, createdFromOperationId: command.operationId, sourceDeviceId: 'phone-1' });
});

test('server gate accepts only the exact Native4 isolated prefix', () => {
  assert.doesNotThrow(() => testStoreOnly({ storeId: 'test-native4-phone' }));
  for (const storeId of ['alsafa-live', 'test-alsafa', 'alsafa-test', 'test-native4', '', null]) {
    assert.throws(() => testStoreOnly({ storeId }), { code: 'TEST_STORE_REQUIRED' });
  }
});

const sale = {
  ...command, kind: 'SALE', recordId: 'sale-001',
  payload: { items: [{ productId: ' product-001 ', quantity: 1, unitPrice: 25 }], currency: 'USD', paymentMethod: 'CASH', total: 25, customerId: ' customer-1 ' }
};

test('builds a normalized sale document without mutating stock itself', () => {
  assert.deepEqual(saleDocument(sale), {
    id: 'sale-001', items: [{ productId: 'product-001', quantity: 1, unitPrice: 25 }], currency: 'USD', paymentMethod: 'CASH', total: 25,
    customerId: 'customer-1', createdFromOperationId: command.operationId, sourceDeviceId: 'phone-1'
  });
});

test('refuses sale writes outside the explicit Native4 boundary', () => {
  assert.throws(() => saleDocument({ ...sale, storeId: 'alsafa-live' }), { code: 'TEST_STORE_REQUIRED' });
});
