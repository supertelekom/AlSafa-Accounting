'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { productDocument, saleDocument } = require('../handlers');

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

test('refuses sale writes outside the explicit test-store boundary', () => {
  assert.throws(() => saleDocument({ ...sale, storeId: 'alsafa-live' }), { code: 'TEST_STORE_REQUIRED' });
});
