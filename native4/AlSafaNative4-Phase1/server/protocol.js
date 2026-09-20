'use strict';

const ALLOWED_KINDS = new Set(['TEST', 'SALE', 'REPAIR', 'PAYMENT', 'PURCHASE', 'EXPENSE', 'CASH_MOVEMENT', 'INVENTORY_ADJUSTMENT', 'PRODUCT_CREATE', 'PRODUCT_UPDATE', 'SETTINGS_UPDATE']);

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }

function productText(value, field, max) {
  const text = String(value || '').trim();
  if (!text || text.length > max) fail('INVALID_PRODUCT_PAYLOAD', `حقل المنتج غير صالح: ${field}`);
  return text;
}

function validatePayload(kind, payload) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) fail('INVALID_PAYLOAD', 'بيانات العملية غير صالحة');
  if (kind === 'TEST') {
    if (typeof payload.note !== 'string' || payload.note.length > 240) fail('INVALID_TEST_PAYLOAD', 'بيانات الاختبار غير صالحة');
  }
  if (kind === 'PRODUCT_CREATE') {
    productText(payload.name, 'name', 140);
    if (payload.barcode != null && String(payload.barcode).trim().length > 80) fail('INVALID_PRODUCT_PAYLOAD', 'باركود المنتج طويل جداً');
    if (!Number.isInteger(payload.initialQuantity) || payload.initialQuantity < 0) fail('INVALID_PRODUCT_PAYLOAD', 'كمية المنتج الافتتاحية غير صالحة');
    if (typeof payload.price !== 'number' || !Number.isFinite(payload.price) || payload.price < 0) fail('INVALID_PRODUCT_PAYLOAD', 'سعر المنتج غير صالح');
  }
}

function validateCommand(raw) {
  if (!raw || typeof raw !== 'object') fail('INVALID_COMMAND', 'الأمر غير صالح');
  const operationId = String(raw.operationId || '').trim();
  const storeId = String(raw.storeId || '').trim();
  const sourceDeviceId = String(raw.sourceDeviceId || '').trim();
  const kind = String(raw.kind || '').trim();
  const recordId = String(raw.recordId || '').trim();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(operationId)) fail('INVALID_OPERATION_ID', 'معرّف العملية غير صالح');
  if (!storeId || !sourceDeviceId || !recordId) fail('MISSING_REFERENCE', 'مرجع المتجر أو الجهاز أو العملية مفقود');
  if (!ALLOWED_KINDS.has(kind)) fail('UNSUPPORTED_KIND', 'نوع العملية غير مدعوم');
  validatePayload(kind, raw.payload);
  return { operationId, storeId, sourceDeviceId, kind, recordId, payload: raw.payload };
}

module.exports = { ALLOWED_KINDS, validateCommand, validatePayload };
