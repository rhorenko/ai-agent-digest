# Testing

~~~powershell
.\mvnw.cmd verify
.\mvnw.cmd '-Dtest=QaBoundaryTests' test
~~~

Normal verify requires Docker and runs real Mongo 8.0.16 Testcontainers, localhost HTTP stubs and plain logic tests. One Spring lifecycle test verifies configuration/startup failure. Automated tests never call paid Claude or real Telegram.

If Docker is unavailable explicitly use -Ddigest.skipMongoTests=true and record persistence as unverified. Normal acceptance requires zero skips. Cleanup drops only a generated test database inside its disposable container.

Coverage: valid/empty/irrelevant selection; missing/extra/duplicate/unknown/invalid model fields; English text limits; dates/window/XXE; URL normalization; persistence/order/dedup/reruns; lock ownership/crash; safe recovery without Claude; database failure before delivery; Telegram success/rejection/429/timeout/unknown; context/HTTP limits; configuration and exits. Fixtures cannot prove real model judgment.

For offline execution see README. Use separate JAR/container databases ending _offline. After two runs in a fresh fixture database:

~~~javascript
db.news.countDocuments({})                  // 1
db.news.countDocuments({status:"DELIVERED"}) // 1
db.runs.find({}, {status:1,exitCode:1,delivered:1})
db.locks.countDocuments({})                 // 0
~~~

Check $LASTEXITCODE immediately. Busy/unresolved/config-failure container paths must return 3/2/1. Evidence is recorded in verification-report.md and acceptance-matrix.md.
