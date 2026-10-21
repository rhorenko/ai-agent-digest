FROM eclipse-temurin:25.0.2_10-jdk-noble AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25.0.2_10-jre-noble AS runtime

RUN groupadd --system app && useradd --system --gid app --home-dir /app --no-create-home app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/agent-digest-0.0.1-SNAPSHOT.jar application.jar

USER app
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
