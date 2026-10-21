package com.agents.digest;

import java.io.IOException;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

public final class HttpTransport implements AutoCloseable {
    private final HttpClient client;
    private final int maxBytes;

    public HttpTransport(Duration timeout, int maxBytes) {
        this.maxBytes = maxBytes;
        client = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException {
        // Future deadline also bounds slow response bodies, not just response headers.
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, info -> limitedBody());
        try {
            return future.get(request.timeout().orElse(Duration.ofSeconds(30)).toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new HttpTimeoutException("HTTP request timed out");
        } catch (ExecutionException e) {
            throw new IOException("HTTP transport failed");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private HttpResponse.BodySubscriber<byte[]> limitedBody() {
        HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        AtomicInteger count = new AtomicInteger();
        return new HttpResponse.BodySubscriber<>() {
            private Flow.Subscription subscription;
            public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
            public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
            public void onNext(List<ByteBuffer> buffers) {
                int bytes = buffers.stream().mapToInt(ByteBuffer::remaining).sum();
                if (count.addAndGet(bytes) > maxBytes) {
                    subscription.cancel();
                    delegate.onError(new IOException("HTTP response exceeds configured limit"));
                } else {
                    delegate.onNext(buffers);
                }
            }
            public void onError(Throwable error) { delegate.onError(error); }
            public void onComplete() { delegate.onComplete(); }
        };
    }

    @Override public void close() { client.close(); }
}
