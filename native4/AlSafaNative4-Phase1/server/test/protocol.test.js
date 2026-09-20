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
