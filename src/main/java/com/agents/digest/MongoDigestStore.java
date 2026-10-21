package com.agents.digest;

import com.agents.digest.News.*;
import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import org.bson.Document;
import java.time.*;
import java.util.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public final class MongoDigestStore implements DigestStore {
    private final MongoCollection<Document> news;
    private final MongoCollection<Document> locks;
    private final MongoCollection<Document> runs;
    private final Clock clock;

    public MongoDigestStore(MongoDatabase database, Clock clock) {
        database = database.withWriteConcern(WriteConcern.MAJORITY);
        news = database.getCollection("news");
        locks = database.getCollection("locks");
        runs = database.getCollection("runs");
        this.clock = clock;
    }

    @Override public void initialize() {
        news.createIndex(Indexes.ascending("url"), new IndexOptions().unique(true));
        news.createIndex(Indexes.compoundIndex(Indexes.ascending("status"), Indexes.descending("score")));
    }

    @Override public boolean acquire(String owner) {
        try {
            locks.insertOne(new Document("_id", "batch").append("owner", owner).append("acquiredAt", date(clock.instant())));
            return true;
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) return false;
            throw e;
        }
    }

    @Override public void release(String owner) { locks.deleteOne(and(eq("_id", "batch"), eq("owner", owner))); }

    @Override public void startRun(String owner) {
        runs.insertOne(new Document("_id", owner).append("startedAt", date(clock.instant())).append("status", "RUNNING"));
    }

    @Override public void finishRun(String owner, Result result, String error) {
        requireUpdated(runs.updateOne(eq("_id", owner), combine(set("finishedAt", date(clock.instant())),
                set("status", result.status()), set("exitCode", result.exitCode()), set("delivered", result.delivered()),
                set("unresolved", result.unresolved()), set("error", error))));
    }

    @Override public void recoverInterrupted() {
        news.updateMany(eq("status", DeliveryState.SENDING.name()), combine(set("status", DeliveryState.UNKNOWN.name()),
                set("error", "INTERRUPTED_DELIVERY"), set("updatedAt", date(clock.instant()))));
    }

    @Override public boolean exists(String url) { return news.countDocuments(eq("url", url)) != 0; }

    @Override public void save(Item item) {
        Candidate c = item.candidate();
        Analysis a = item.analysis();
        Document record = new Document("url", c.url()).append("candidateId", c.id()).append("title", c.title())
                .append("source", c.source()).append("publishedAt", date(c.publishedAt()))
                .append("discoveredAt", date(c.discoveredAt())).append("excerpt", c.excerpt()).append("material", c.material())
                .append("score", a.score()).append("summary", a.summary()).append("relevance", a.relevance())
                .append("takeaway", a.takeaway()).append("scoreReason", a.scoreReason())
                .append("processedAt", date(item.processedAt())).append("promptVersion", "analysis-v1")
                .append("status", DeliveryState.PENDING.name()).append("attempts", 0);
        // Upsert is insert-only: never overwrite an earlier delivery outcome.
        news.updateOne(eq("url", c.url()), new Document("$setOnInsert", record), new UpdateOptions().upsert(true));
    }

    @Override public List<Pending> pending(int limit) {
        List<Pending> pending = new ArrayList<>();
        news.find(and(in("status", DeliveryState.PENDING.name(), DeliveryState.RETRY.name()),
                        or(Filters.exists("retryAt", false), eq("retryAt", null), lte("retryAt", date(clock.instant())))))
                .sort(Sorts.orderBy(Sorts.descending("score"), Sorts.ascending("url"))).limit(limit)
                .forEach(doc -> pending.add(new Pending(doc.getString("url"), fromDocument(doc))));
        return pending;
    }

    @Override public void markSending(String url) {
        requireUpdated(news.updateOne(and(eq("url", url), in("status", "PENDING", "RETRY")),
                combine(set("status", "SENDING"), set("attemptedAt", date(clock.instant())), inc("attempts", 1))));
    }

    @Override public void recordDelivery(String url, Delivery delivery) {
        requireUpdated(news.updateOne(and(eq("url", url), eq("status", "SENDING")), combine(
                set("status", delivery.state().name()), set("telegramMessageId", delivery.messageId()),
                set("error", delivery.error()), set("retryAt", date(delivery.retryAt())),
                set("updatedAt", date(clock.instant())))));
    }

    @Override public long unresolved() { return news.countDocuments(ne("status", "DELIVERED")); }

    private static Item fromDocument(Document d) {
        Candidate c = new Candidate(d.getString("candidateId"), d.getString("title"), d.getString("source"),
                d.getString("url"), instant(d, "publishedAt"), instant(d, "discoveredAt"),
                d.getString("excerpt"), d.getString("material"));
        Analysis a = new Analysis(c.id(), d.getInteger("score"), d.getString("summary"), d.getString("relevance"),
                d.getString("takeaway"), d.getString("scoreReason"));
        return new Item(c, a, instant(d, "processedAt"));
    }

    private static Date date(Instant value) { return value == null ? null : Date.from(value); }
    private static Instant instant(Document document, String key) {
        Date date = document.getDate(key);
        return date == null ? null : date.toInstant();
    }
    private static void requireUpdated(com.mongodb.client.result.UpdateResult result) {
        if (result.getMatchedCount() != 1) throw new IllegalStateException("DATABASE_STATE_CONFLICT");
    }
}
