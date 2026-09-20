/* Phase 1 integration: durable LOCAL_ONLY product observations, not server commands. */
(function(root) {
  'use strict';
  const copy = value => JSON.parse(JSON.stringify(value));
  function canonical(value) {
    if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
    if (value && typeof value === 'object') return '{' + Object.keys(value).sort().filter(k => !k.startsWith('_sync')).map(k => JSON.stringify(k)+':'+canonical(value[k])).join(',') + '}';
    return JSON.stringify(value);
  }
  function rowsById(rows) {
    const result = new Map();
    for (const row of rows || []) {
      if (!row || typeof row.id !== 'string' || !row.id.trim() || result.has(row.id)) throw Error('INVALID_OR_DUPLICATE_PRODUCT_ID');
      result.set(row.id, row);
    }
    return result;
  }
  function stage(previous, next, deviceId, makeId, now) {
    if (!deviceId) throw Error('DEVICE_ID_REQUIRED');
    const existing = previous.productJournal;
    if (existing && (existing.version !== 1 || !Array.isArray(existing.entries))) throw Error('UNSUPPORTED_JOURNAL');
    const journal = existing ? copy(existing) : { version: 1, mode: 'LOCAL_ONLY', entries: [] };
    const before = rowsById(previous.products), after = rowsById(next.products);
    for (const id of before.keys()) if (!after.has(id)) throw Error('PRODUCT_REMOVAL_BLOCKED');
    const ids = new Set(journal.entries.map(e => e.id));
    for (const [recordId, product] of after) {
      const old = before.get(recordId);
      if (old && canonical(old) === canonical(product)) continue;
      if (!String(product.name || '').trim() || !Number.isFinite(product.stock) || product.stock < 0 || !Number.isFinite(product.salePrice) || product.salePrice < 0) throw Error('INVALID_PRODUCT');
      const id = makeId();
      if (typeof id !== 'string' || !id.trim() || ids.has(id)) throw Error('DUPLICATE_OPERATION_ID');
      ids.add(id);
      journal.entries.push({id, recordId, sourceDeviceId: deviceId, createdAt: now,
        kind: old ? 'PRODUCT_UPDATE_OBSERVED' : 'PRODUCT_CREATE_OBSERVED', status: 'LOCAL_ONLY',
        before: old ? copy(old) : null, after: copy(product)});
    }
    return journal;
  }
  root.AlSafaProductJournal = Object.freeze({stage});
})(typeof globalThis === 'object' ? globalThis : window);
