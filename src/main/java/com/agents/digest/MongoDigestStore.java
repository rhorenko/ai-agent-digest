package com.agents.digest;

import com.agents.digest.news.AnalysisValidator.Selected;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

final class MongoDigestStore implements DigestStore {
    private final MongoClient client;
    private final MongoCollection<Document> items;
    private final MongoCollection<Document> locks;
    private final String owner = UUID.randomUUID().toString();
    MongoDigestStore(DigestProperties config) {
        client = MongoClients.create(config.mongoUri());
        MongoDatabase database = client.getDatabase(config.mongoDatabase());
        items = database.getCollection("news_items");
        locks = database.getCollection("batch_locks");
        items.createIndex(Indexes.ascending("url"), new IndexOptions().unique(true));
    }
    public void lock() {
        try { locks.insertOne(new Document("_id", "digest").append("owner", owner).append("startedAt", new Date())); }
        catch (MongoWriteException e) {
            if (e.getError().getCode() == 11000) throw new IllegalStateException("Another batch holds the lock, or a crashed batch needs review");
            throw e;
        }
    }
    public void unlock() { locks.deleteOne(and(eq("_id", "digest"), eq("owner", owner))); }
    public boolean contains(String url) { return items.countDocuments(eq("url", url)) > 0; }
    public void insert(Selected selected) {
        var c = selected.candidate(); var a = selected.analysis();
        Document doc = new Document("url", c.url()).append("title", c.title()).append("source", c.source())
                .append("publishedAt", Date.from(c.publishedAt())).append("discoveredAt", Date.from(c.discoveredAt()))
                .append("excerpt", c.excerpt()).append("summary", a.summary()).append("relevance", a.relevance())
                .append("takeaway", a.takeaway()).append("scoreExplanation", a.scoreExplanation()).append("score", a.importance())
                .append("processedAt", Date.from(Instant.now())).append("status", "PENDING");
        items.insertOne(doc);
    }
    public List<Item> pending() {
        List<Item> result = new ArrayList<>();
        for (Document d : items.find(and(in("status", "PENDING", "FAILED"), or(exists("retryAt", false), eq("retryAt", null), lte("retryAt", new Date())))).sort(Sorts.orderBy(Sorts.descending("score"), Sorts.ascending("processedAt")))) {
            result.add(new Item(d.getString("url"), d.getString("title"), d.getString("source"), d.getInteger("score"), d.getString("summary"), d.getString("relevance"), d.getString("takeaway")));
        }
        return result;
    }
    public boolean hasUncertain() { return items.countDocuments(in("status", "FAILED", "SENDING", "AMBIGUOUS")) > 0; }
    public void sending(String url) {
        if (items.updateOne(and(eq("url", url), in("status", "PENDING", "FAILED")), combine(set("status", "SENDING"), set("deliveryAttemptAt", new Date()), unset("error"), unset("retryAt"))).getModifiedCount() != 1)
            throw new IllegalStateException("Could not claim persisted item");
    }
    public void finish(String url, String state, String messageId, String error, long retryDelaySeconds) {
        Date retryAt = retryDelaySeconds > 0 ? Date.from(Instant.now().plusSeconds(retryDelaySeconds)) : null;
        if (items.updateOne(and(eq("url", url), eq("status", "SENDING")), combine(set("status", state), set("messageId", messageId), set("error", error), set("retryAt", retryAt), set("deliveryUpdatedAt", new Date()))).getMatchedCount() != 1)
            throw new IllegalStateException("Could not save delivery result");
    }
    public void close() { client.close(); }
}


