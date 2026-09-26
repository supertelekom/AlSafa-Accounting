'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { validateCommand } = require('../protocol');

const valid = { operationId: '2b0b38b8-4dc1-4f4d-8e27-8a7b88c1ef14', storeId: 'alsafa-test', sourceDeviceId: 'phone-1', kind: 'TEST', recordId: 'test-1', payload: { note: 'safe' } };
test('accepts one complete command envelope', () => assert.equal(validateCommand(valid).operationId, valid.operationId));
test('rejects a duplicate-prone empty id', () => assert.throws(() => validateCommand({ ...valid, operationId: '' }), { code: 'INVALID_OPERATION_ID' }));
test('rejects unknown operation kinds', () => assert.throws(() => validateCommand({ ...valid, kind: 'DELETE_ALL' }), { code: 'UNSUPPORTED_KIND' }));
test('rejects lowercase kinds so clients cannot create a second command dialect', () => assert.throws(() => validateCommand({ ...valid, kind: 'test' }), { code: 'UNSUPPORTED_KIND' }));
test('rejects a non-object payload', () => assert.throws(() => validateCommand({ ...valid, payload: [] }), { code: 'INVALID_PAYLOAD' }));
test('rejects a command without a store boundary', () => assert.throws(() => validateCommand({ ...valid, storeId: ' ' }), { code: 'MISSING_REFERENCE' }));

const product = { ...valid, kind: 'PRODUCT_CREATE', recordId: 'product-test-1', payload: { name: 'ساعة اختبار', barcode: 'TEST-001', initialQuantity: 1, price: 25 } };
test('accepts a complete product-create command envelope', () => assert.equal(validateCommand(product).payload.price, 25));
test('rejects a product with negative opening quantity', () => assert.throws(() => validateCommand({ ...product, payload: { ...product.payload, initialQuantity: -1 } }), { code: 'INVALID_PRODUCT_PAYLOAD' }));
test('rejects a product without a name', () => assert.throws(() => validateCommand({ ...product, payload: { ...product.payload, name: ' ' } }), { code: 'INVALID_PRODUCT_PAYLOAD' }));

const productUpdate = { ...valid, kind: 'PRODUCT_UPDATE', recordId: 'product-test-1', payload: { name: 'اسم معدل', price: 30 } };
test('accepts product metadata update', () => assert.equal(validateCommand(productUpdate).payload.price, 30));
test('rejects quantity inside product update so stock cannot be overwritten', () => assert.throws(() => validateCommand({ ...productUpdate, payload: { quantity: 999 } }), { code: 'INVALID_PRODUCT_UPDATE' }));
test('rejects initialQuantity inside product update', () => assert.throws(() => validateCommand({ ...productUpdate, payload: { initialQuantity: 999 } }), { code: 'INVALID_PRODUCT_UPDATE' }));
test('rejects empty product update', () => assert.throws(() => validateCommand({ ...productUpdate, payload: {} }), { code: 'INVALID_PRODUCT_UPDATE' }));

const inventory = { ...valid, kind: 'INVENTORY_ADJUSTMENT', recordId: 'product-test-1', payload: { quantityDelta: -1, reason: 'count correction' } };
test('accepts explicit inventory adjustment', () => assert.equal(validateCommand(inventory).payload.quantityDelta, -1));
test('rejects zero inventory adjustment', () => assert.throws(() => validateCommand({ ...inventory, payload: { quantityDelta: 0, reason: 'none' } }), { code: 'INVALID_INVENTORY_ADJUSTMENT' }));

const cash = { ...valid, kind: 'CASH_MOVEMENT', recordId: 'transfer-1', payload: { fromAccount: 'DRAWER', toAccount: 'EXTERNAL', amount: 20, currency: 'USD', reason: 'safe transfer' } };
test('accepts a balanced cash transfer envelope', () => assert.equal(validateCommand(cash).payload.amount, 20));
test('rejects transfer to same account', () => assert.throws(() => validateCommand({ ...cash, payload: { ...cash.payload, toAccount: 'DRAWER' } }), { code: 'INVALID_CASH_MOVEMENT' }));
test('rejects unsupported transfer currency', () => assert.throws(() => validateCommand({ ...cash, payload: { ...cash.payload, currency: 'EUR' } }), { code: 'INVALID_CASH_MOVEMENT' }));
test('rejects zero cash transfer', () => assert.throws(() => validateCommand({ ...cash, payload: { ...cash.payload, amount: 0 } }), { code: 'INVALID_CASH_MOVEMENT' }));

const expense = { ...valid, kind: 'EXPENSE', recordId: 'expense-1', payload: { account: 'DRAWER', amount: 10, currency: 'USD', reason: 'rent' } };
test('accepts expense from explicit account', () => assert.equal(validateCommand(expense).payload.account, 'DRAWER'));
test('rejects expense without positive amount', () => assert.throws(() => validateCommand({ ...expense, payload: { ...expense.payload, amount: -1 } }), { code: 'INVALID_EXPENSE' }));

const sale = { ...valid, kind: 'SALE', recordId: 'sale-test-1', payload: { items: [{ productId: 'product-test-1', quantity: 2, unitPrice: 25 }], currency: 'USD', paymentMethod: 'CASH', total: 50 } };
test('accepts a complete sale command envelope', () => assert.equal(validateCommand(sale).payload.total, 50));
test('rejects sale total that differs from its items', () => assert.throws(() => validateCommand({ ...sale, payload: { ...sale.payload, total: 49 } }), { code: 'INVALID_SALE_PAYLOAD' }));
test('rejects zero sale quantity', () => assert.throws(() => validateCommand({ ...sale, payload: { ...sale.payload, items: [{ productId: 'product-test-1', quantity: 0, unitPrice: 25 }], total: 0 } }), { code: 'INVALID_SALE_PAYLOAD' }));
test('rejects unsupported sale currency', () => assert.throws(() => validateCommand({ ...sale, payload: { ...sale.payload, currency: 'EUR' } }), { code: 'INVALID_SALE_PAYLOAD' }));
test('rejects duplicate product lines so stock cannot be decremented twice ambiguously', () => assert.throws(() => validateCommand({ ...sale, payload: { ...sale.payload, items: [{ productId: 'p1', quantity: 1, unitPrice: 25 }, { productId: 'p1', quantity: 1, unitPrice: 25 }] } }), { code: 'INVALID_SALE_PAYLOAD' }));
