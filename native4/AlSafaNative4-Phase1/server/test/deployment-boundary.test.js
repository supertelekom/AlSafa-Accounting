'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');

test('Native 4 Firestore rules deny direct Android client access', () => {
  const rules = fs.readFileSync(path.join(root, 'firestore-native4.rules'), 'utf8');
  assert.match(rules, /match\s+\/native4Stores\/{storeId\}/);
  assert.match(rules, /allow\s+read,\s*write:\s*if\s+false;/);
});

test('the test deployment config cannot silently replace Firestore rules', () => {
  const config = JSON.parse(fs.readFileSync(path.join(root, '..', 'firebase.json'), 'utf8'));
  assert.equal(Object.hasOwn(config, 'firestore'), false);
  assert.equal(config.functions[0].source, 'server');
});
