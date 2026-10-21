# One-shot execution and recovery

Start Mongo with docker compose -f docker/compose.yaml up -d --wait mongo, then scripts/run-container.ps1. Scripts optionally load root .env. Direct Compose can use --env-file .env. Java uses process environment or external configuration.

Mongo 8.0.16 uses persistent mongo-data, loopback port 27019 and a readiness health check. No app ports, scheduler or restart policy. This setup is local; configure authentication/TLS/backups for other deployments.

| Exit | Meaning |
|---|---|
| 0 | Completed, including empty selection/rerun |
| 1 | Operational/configuration/lock-release failure |
| 2 | Unresolved delivery remains |
| 3 | Another batch owns the database lock |

news contains source/analysis, publication/discovery/processing time, excerpt/material, score/reason, promptVersion, status, attempts, attemptedAt, updatedAt, retryAt, safe error category and Telegram message ID. runs contains start/end/status/exit/counts. locks identifies the exclusive batch owner. Early fatal failures can leave incomplete counts; inspect news. Database outage can prevent final run audit.

All selected items persist before sending; insert-only upsert and unique normalized URL preserve prior delivery history. Each attempt persists SENDING before HTTP. Delivered URLs never resend. Up to max-items pending deliveries per run, descending score then URL.

## Reprocessing

Pass --digest.recovery-only=true to bypass sources and Claude. Only PENDING/eligible RETRY is attempted. Explicit Telegram 429 is retried within configured budget; longer delays become retryAt. Other items may still be attempted and can also be rate-limited. REJECTED requires correcting the cause and an operator requeue.

Timeout, transport loss, 5xx, malformed success and interruption become UNKNOWN, never automatically resent. Stranded SENDING becomes UNKNOWN on the next exclusive run. Telegram acceptance followed by Mongo failure remains ambiguous: no exactly-once guarantee.

## Reconcile one delivery

Stop batches for this database. Inspect news, runs and the target chat. Use interactive mongosh against the correct database, inspecting the exact URL first:

~~~javascript
db.news.find({status: {$in: ["UNKNOWN", "REJECTED", "SENDING"]}})
db.news.findOne({url: "<exact normalized URL>"})
// Only if the message is verified in Telegram:
db.news.updateOne({url: "<exact normalized URL>", status: "UNKNOWN"},
  {$set: {status: "DELIVERED", telegramMessageId: NumberLong("<verified message ID>"),
          error: "OPERATOR_RECONCILED", updatedAt: new Date()}})
// Only after confirming no message was sent, or accepting duplicate risk:
db.news.updateOne({url: "<exact normalized URL>", status: "UNKNOWN"},
  {$set: {status: "RETRY", retryAt: new Date(), error: "OPERATOR_REQUEUE"}})
~~~

For REJECTED use that exact predicate after fixing chat/permissions/configuration. Do not broadly reset delivery history or delete news to retry.

## Stale lock

The singleton mutex has no automatic expiry: a paused process must not resume beside another sender. Confirm the owning process/container stopped before removing its exact lock:

~~~javascript
db.locks.findOne({_id: "batch"})
db.locks.deleteOne({_id: "batch", owner: "<verified stopped owner>"})
~~~

The next run converts stranded SENDING to UNKNOWN. Mutex/dedup scope is one database; use one live database per destination. Separate databases targeting the same chat do not share deduplication.

## Inspect and stop

~~~powershell
docker compose -f docker/compose.yaml exec mongo mongosh agent_digest
docker compose -f docker/compose.yaml stop mongo
~~~

Retain Mongo volumes and live records. Do not remove volumes or perform broad cleanup. Testcontainers removes only its disposable test resources; offline demo records intentionally remain in separate databases.
