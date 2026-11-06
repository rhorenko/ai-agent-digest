package com.agents.digest;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Enforces both a response byte limit and an end-to-end deadline, including response body reads. */
final class HttpTransport {
    private HttpTransport() { }
    static HttpResponse<byte[]> send(HttpClient http, HttpRequest request, int maxBytes, int timeoutSeconds) throws IOException, InterruptedException {
        var future = http.sendAsync(request, info -> new LimitedBody(maxBytes));
        try { return future.get(timeoutSeconds, TimeUnit.SECONDS); }
        catch (TimeoutException e) { future.cancel(true); throw new IOException("HTTP request deadline exceeded", e); }
        catch (ExecutionException e) { throw new IOException("HTTP request failed", e.getCause()); }
        catch (InterruptedException e) { future.cancel(true); throw e; }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int max;
        private Flow.Subscription subscription;
        private long count;
        private boolean done;
        LimitedBody(int max) { this.max = max; }
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; delegate.onSubscribe(subscription); }
        public void onNext(List<ByteBuffer> chunks) {
            if (done) return;
            for (ByteBuffer chunk : chunks) count += chunk.remaining();
            if (count > max) { done = true; subscription.cancel(); delegate.onError(new IOException("HTTP response exceeds size limit")); }
            else delegate.onNext(chunks);
        }
        public void onError(Throwable error) { if (!done) { done = true; delegate.onError(error); } }
        public void onComplete() { if (!done) { done = true; delegate.onComplete(); } }
    }
}
