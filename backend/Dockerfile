FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src

RUN mvn -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/backend/target/ai-resume-optimizer-backend-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

