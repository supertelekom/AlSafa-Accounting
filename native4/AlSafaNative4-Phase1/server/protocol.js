'use strict';

const ALLOWED_KINDS = new Set(['TEST', 'SALE', 'REPAIR', 'PAYMENT', 'PURCHASE', 'EXPENSE', 'CASH_MOVEMENT', 'INVENTORY_ADJUSTMENT', 'PRODUCT_CREATE', 'PRODUCT_UPDATE', 'SETTINGS_UPDATE']);
const CURRENCIES = new Set(['USD', 'SYP', 'TRY']);
const PAYMENT_METHODS = new Set(['CASH', 'SHAM_CASH', 'LATER']);

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }
function finiteNonNegative(value, code, message) { if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) fail(code, message); }
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
    finiteNonNegative(payload.price, 'INVALID_PRODUCT_PAYLOAD', 'سعر المنتج غير صالح');
  }
  if (kind === 'PRODUCT_UPDATE') {
    const allowed = new Set(['name', 'barcode', 'price']);
    const keys = Object.keys(payload);
    if (!keys.length || keys.some(key => !allowed.has(key))) fail('INVALID_PRODUCT_UPDATE', 'تعديل المنتج يحتوي حقلاً غير مسموح');
    if (Object.prototype.hasOwnProperty.call(payload, 'name')) productText(payload.name, 'name', 140);
    if (Object.prototype.hasOwnProperty.call(payload, 'barcode') && payload.barcode != null && String(payload.barcode).trim().length > 80) fail('INVALID_PRODUCT_UPDATE', 'باركود المنتج طويل جداً');
    if (Object.prototype.hasOwnProperty.call(payload, 'price')) finiteNonNegative(payload.price, 'INVALID_PRODUCT_UPDATE', 'سعر المنتج غير صالح');
  }
  if (kind === 'SALE') {
    if (!Array.isArray(payload.items) || payload.items.length < 1 || payload.items.length > 100) fail('INVALID_SALE_PAYLOAD', 'يجب أن يحتوي البيع على عنصر واحد على الأقل');
    const ids = new Set();
    for (const item of payload.items) {
      if (!item || typeof item !== 'object' || Array.isArray(item)) fail('INVALID_SALE_PAYLOAD', 'عنصر البيع غير صالح');
      const productId = String(item.productId || '').trim();
      if (!productId || productId.length > 160 || ids.has(productId)) fail('INVALID_SALE_PAYLOAD', 'معرّف منتج البيع مفقود أو مكرر');
      ids.add(productId);
      if (!Number.isInteger(item.quantity) || item.quantity <= 0) fail('INVALID_SALE_PAYLOAD', 'كمية البيع غير صالحة');
      finiteNonNegative(item.unitPrice, 'INVALID_SALE_PAYLOAD', 'سعر البيع غير صالح');
    }
    if (!CURRENCIES.has(payload.currency)) fail('INVALID_SALE_PAYLOAD', 'عملة البيع غير مدعومة');
    if (!PAYMENT_METHODS.has(payload.paymentMethod)) fail('INVALID_SALE_PAYLOAD', 'طريقة الدفع غير مدعومة');
    finiteNonNegative(payload.total, 'INVALID_SALE_PAYLOAD', 'إجمالي البيع غير صالح');
    const calculated = payload.items.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0);
    if (Math.abs(calculated - payload.total) > 0.000001) fail('INVALID_SALE_PAYLOAD', 'إجمالي البيع لا يطابق العناصر');
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

module.exports = { ALLOWED_KINDS, CURRENCIES, PAYMENT_METHODS, validateCommand, validatePayload };
