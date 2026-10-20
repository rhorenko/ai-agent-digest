# Troubleshooting

## Wrong Java version

If compilation reports an unsupported release, run `java -version` and `.\mvnw.cmd -version`. Both must resolve a JDK 25; a JRE alone cannot compile.

## Wrapper download failure

The first Wrapper run downloads the pinned Maven distribution from Maven Central. Check network/proxy settings and the URL in `.mvn/wrapper/maven-wrapper.properties`; do not replace the wrapper with an unpinned system build in verification evidence.

## Tests do not see the startup message

Run `.\mvnw.cmd -Dtest=AgentDigestApplicationTests test` and inspect standard output. Preserve `spring.main.web-application-type=none` and ensure the demonstration or replacement runner is still a Spring bean.

## JAR does not start

Run `.\mvnw.cmd verify`, then inspect `target/` and use the non-`.original` JAR. The Spring Boot plugin creates the executable artifact.

## Docker build cannot find files

Run the command from the repository root and keep the context as `.`. For Compose use `docker compose -f docker/compose.yaml config` to confirm `context: ..` resolves to the root.

## Container exits immediately

That is expected after a successful one-shot run. Check the log message and exit code. A non-zero code or stack trace indicates failure; do not add a restart loop to hide it.

## Docker daemon unavailable

Start Docker Desktop and run `docker version`. Maven and local JAR checks remain valid independently; record container checks as not run until the daemon works.
