package com.alsafa.native4.sync;

/**
 * Safety transport used by the local-core build. It performs no network I/O.
 * A real authenticated Firebase callable transport will replace this only in a
 * dedicated test-store build after sender-state tests pass.
 */
public final class DormantCommandTransport implements OutboxSender.Transport {
 @Override public OutboxSender.Result submit(OutboxSender.Command command) throws OutboxSender.RetryableException {
  throw new OutboxSender.RetryableException("الشبكة غير مفعلة في نسخة local-core؛ بقي operationId محفوظاً لإعادة المحاولة");
 }
}
