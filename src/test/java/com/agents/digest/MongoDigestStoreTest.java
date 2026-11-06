package com.agents.digest;

import com.agents.digest.news.AnalysisValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class MongoDigestStoreTest {
    @Container static final MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");
    @Test void persistsDeduplicatesAndProtectsAmbiguousDeliveryAndLock() {
        var cfg=new DigestProperties(true,mongo.getReplicaSetUrl(),"digest_test","","model","","",List.of(),"classpath:prompts/profile-v1.txt",24,5,30,5,512,2);
        try (var first=new MongoDigestStore(cfg); var second=new MongoDigestStore(cfg)) {
            first.lock(); assertThrows(IllegalStateException.class,second::lock);
            var c=DigestBatchTest.candidate("https://example.org/mongo");
            var item=new AnalysisValidator.Selected(c,DigestBatchTest.analysis(c.id(),9));
            first.insert(item); assertThrows(com.mongodb.MongoWriteException.class,() -> first.insert(item));
            assertTrue(second.contains(c.url())); assertEquals(1,second.pending().size());
            first.sending(c.url()); assertTrue(second.pending().isEmpty()); assertTrue(second.hasUncertain());
            first.finish(c.url(),"FAILED",null,"rate limit",3600); assertTrue(second.pending().isEmpty()); assertTrue(second.hasUncertain());
            try (var client = com.mongodb.client.MongoClients.create(cfg.mongoUri())) {
                client.getDatabase(cfg.mongoDatabase()).getCollection("news_items").updateOne(new org.bson.Document("url", c.url()), new org.bson.Document("$set", new org.bson.Document("retryAt", new java.util.Date(0))));
            }
            assertEquals(1,second.pending().size()); first.sending(c.url());
            first.finish(c.url(),"SENT","42",null,0); assertFalse(second.hasUncertain()); assertTrue(second.pending().isEmpty());
            first.unlock(); second.lock(); second.unlock();
        }
    }
}


