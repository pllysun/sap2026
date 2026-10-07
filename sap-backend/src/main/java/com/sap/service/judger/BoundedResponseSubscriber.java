package com.sap.service.judger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Enforces the byte limit before retaining each received chunk. */
final class BoundedResponseSubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private Flow.Subscription subscription;

    BoundedResponseSubscriber(int limit) { this.limit = limit; }

    @Override public CompletionStage<byte[]> getBody() { return body; }

    @Override public void onSubscribe(Flow.Subscription value) {
        if (subscription != null) { value.cancel(); return; }
        subscription = value;
        value.request(1);
    }

    @Override public void onNext(List<ByteBuffer> buffers) {
        if (body.isDone()) return;
        long incoming = buffers.stream().mapToLong(ByteBuffer::remaining).sum();
        if (incoming > limit - bytes.size()) {
            subscription.cancel();
            body.completeExceptionally(new IOException("Node response exceeds byte limit"));
            return;
        }
        for (ByteBuffer buffer : buffers) {
            byte[] chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            bytes.writeBytes(chunk);
        }
        subscription.request(1);
    }

    @Override public void onError(Throwable error) { body.completeExceptionally(error); }
    @Override public void onComplete() { body.complete(bytes.toByteArray()); }
}
