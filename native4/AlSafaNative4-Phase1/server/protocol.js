'use strict';

const ALLOWED_KINDS = new Set(['TEST', 'SALE', 'REPAIR', 'PAYMENT', 'PURCHASE', 'EXPENSE', 'CASH_MOVEMENT', 'INVENTORY_ADJUSTMENT', 'PRODUCT_CREATE', 'PRODUCT_UPDATE', 'SETTINGS_UPDATE']);

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }

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
  if (!raw.payload || typeof raw.payload !== 'object' || Array.isArray(raw.payload)) fail('INVALID_PAYLOAD', 'بيانات العملية غير صالحة');
  return { operationId, storeId, sourceDeviceId, kind, recordId, payload: raw.payload };
}

module.exports = { ALLOWED_KINDS, validateCommand };
