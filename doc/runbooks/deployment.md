# Deployment and recovery

The job runs once and terminates. Run `docker compose --env-file .env -f docker/compose.yaml run --rm app` from the repository root. The app waits for MongoDB health and returns 0 only on successful completion. It does not restart automatically. Compose stores MongoDB in the named `mongo-data` volume. `docker compose -f docker/compose.yaml stop` stops services without deleting data; never use `down -v` as routine cleanup.

To inspect the bundled MongoDB (use the appropriate project name and database):

```powershell
docker compose -f docker/compose.yaml exec -T mongo mongosh --quiet agent_digest --eval 'db.news_items.find({}, {url:1,status:1,messageId:1,error:1,retryAt:1}).toArray()'
docker compose -f docker/compose.yaml exec -T mongo mongosh --quiet agent_digest --eval 'db.batch_locks.find().toArray()'
```

States: `PENDING` is saved but not attempted; `FAILED` is a definite rejection and retries at or after `retryAt`; `SENDING` or `AMBIGUOUS` means the outcome may be unknown; `SENT` is confirmed. If the process died or a database update failed after Telegram accepted a message, even `SENDING` may represent a delivered message. Inspect the chat and record. Only after making a deliberate recovery decision, update that specific record to `SENT` or `FAILED`, recording appropriate message ID/error. Do not automatically retry uncertain results.

A lock is intentionally not expired automatically. First confirm no batch process is running, inspect every uncertain record, and only then remove the single lock: `db.batch_locks.deleteOne({_id:"digest"})` in the correct database. Never remove the lock while another batch may be active. This is a one-shot local deployment, not a backup, monitoring, authentication or production-hardening solution.
