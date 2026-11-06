package com.agents.digest;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExternalClientsTest {
    static DigestProperties online() { return new DigestProperties(false, "mongodb://localhost", "test_db", "test-key", "test-model", "test-token", "123", List.of("https://example.org/feed"), "classpath:prompts/profile-v1.txt",24,5,30,3,512,2); }
    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int code, String body) throws java.io.IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(code,bytes.length); try (var out=exchange.getResponseBody()) {out.write(bytes);}
    }
    @Test void telegramHandlesRateLimitThenSuccessAndPlainText() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); var calls=new AtomicInteger();
        server.createContext("/send", exchange -> {
            String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            assertFalse(Document.parse(request).containsKey("parse_mode"));
            if (calls.getAndIncrement()==0) reply(exchange,429,"{\"parameters\":{\"retry_after\":1}}");
            else reply(exchange,200,"{\"ok\":true,\"result\":{\"message_id\":42}}");
        }); server.start();
        try {
            var client=new TelegramDelivery(online(),HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/send"));
            var item=new DigestStore.Item("https://example.org/a", "Title <&>", "Source", 8, "summary", "relevance", "action");
            var result=client.send(item); assertEquals("SENT",result.state()); assertEquals("42",result.messageId()); assertEquals(2,calls.get());
            assertTrue(TelegramDelivery.message(item).contains("Title <&>"));
        } finally {server.stop(0);}
    }
    @Test void telegramTreatsServerFailureAndMalformedSuccessAsAmbiguous() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); var calls=new AtomicInteger();
        server.createContext("/send", ex -> { if(calls.getAndIncrement()==0) reply(ex,500,"{}"); else reply(ex,200,"{}"); }); server.start();
        try {
            var client=new TelegramDelivery(online(),HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/send"));
            var item=new DigestStore.Item("https://example.org/a","t","s",1,"s","r","a");
            assertEquals("AMBIGUOUS",client.send(item).state()); assertEquals("AMBIGUOUS",client.send(item).state());
        } finally {server.stop(0);}
    }
    @Test void claudeUsesSchemaAndRejectsMalformedResponse() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/messages", ex -> {
            var request=Document.parse(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            assertNotNull(request.get("output_config"));
            reply(ex,200,"{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"{\\\"items\\\":[{}]}\"}]}");
        }); server.start();
        try {
            var client=new ClaudeAnalyzer(online(),HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/messages"));
            assertThrows(IllegalArgumentException.class, () -> client.analyze(List.of(DigestBatchTest.candidate("https://example.org/a"))));
        } finally {server.stop(0);}
    }
    @Test void oversizedResponseIsAmbiguousAndMessageFitsTelegramLimit() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/large", ex -> reply(ex, 200, "x".repeat(100_001))); server.start();
        try {
            var client=new TelegramDelivery(online(),HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/large"));
            var item=new DigestStore.Item("https://example.org/" + "x".repeat(1700),"😀".repeat(400),"s".repeat(500),5,"a".repeat(4000),"r".repeat(4000),"t".repeat(4000));
            assertTrue(TelegramDelivery.message(item).length() <= 4096);
            assertEquals("AMBIGUOUS",client.send(item).state());
        } finally {server.stop(0);}
    }
    @Test void stalledResponseBodyHasAnEndToEndDeadline() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/slow", ex -> {
            ex.sendResponseHeaders(200, 10);
            try { Thread.sleep(2500); ex.getResponseBody().write("0123456789".getBytes(StandardCharsets.UTF_8)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } catch (java.io.IOException ignored) { }
            finally { ex.close(); }
        }); server.start();
        try {
            var request=java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/slow")).build();
            assertThrows(java.io.IOException.class, () -> HttpTransport.send(HttpClient.newHttpClient(),request,100,1));
        } finally {server.stop(0);}
    }
    @Test void claudeStructuredResponseIsReadFromStub() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/messages", ex -> {
            var request=Document.parse(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            assertEquals("test-model", request.getString("model"));
            assertEquals("json_schema", request.get("output_config",Document.class).get("format",Document.class).getString("type"));
            var item = new Document("candidateId","https://example.org/a").append("importance",8).append("summary","Summary").append("relevance","Useful").append("takeaway","Try it").append("scoreExplanation","Relevant");
            String payload = new Document("items",List.of(item)).toJson();
            reply(ex,200,new Document("stop_reason","end_turn").append("content",List.of(new Document("type","text").append("text",payload))).toJson());
        }); server.start();
        try {
            var client=new ClaudeAnalyzer(online(),HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/messages"));
            var result=client.analyze(List.of(DigestBatchTest.candidate("https://example.org/a")));
            assertEquals(1,result.size()); assertEquals(8,result.getFirst().importance()); assertEquals("https://example.org/a",result.getFirst().candidateId());
        } finally {server.stop(0);}
    }
}



