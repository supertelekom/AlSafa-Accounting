'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { productDocument } = require('../handlers');

const command = {
  operationId: '2b0b38b8-4dc1-4f4d-8e27-8a7b88c1ef14', storeId: 'test-alsafa-native4', sourceDeviceId: 'phone-1',
  kind: 'PRODUCT_CREATE', recordId: 'product-001', payload: { name: ' ساعة اختبار ', barcode: ' TEST-001 ', initialQuantity: 2, price: 25 }
};

test('builds a minimal immutable product document for a test store', () => {
  assert.deepEqual(productDocument(command), { id: 'product-001', name: 'ساعة اختبار', barcode: 'TEST-001', quantity: 2, price: 25, createdFromOperationId: command.operationId, sourceDeviceId: 'phone-1' });
});

test('refuses product writes outside the explicit test-store boundary', () => {
  assert.throws(() => productDocument({ ...command, storeId: 'alsafa-live' }), { code: 'TEST_STORE_REQUIRED' });
});
