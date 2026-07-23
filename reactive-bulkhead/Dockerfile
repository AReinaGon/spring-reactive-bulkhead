# Runtime image for the event-page BFF. It runs a PREBUILT jar so the build uses your local
# Corretto 25 + Maven toolchain (no JDK-25 Maven base image to chase). Build order:
#
#   mvn -DskipTests package                  # produces target/reactive-bulkhead-*.jar
#   docker build -t reactive-bulkhead:local .
#
# The deploy/docker compose file performs both steps for you (build: context points here).
FROM amazoncorretto:25

WORKDIR /app

ARG JAR_FILE=target/reactive-bulkhead-1.0.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080

# JAVA_OPTS is pass-through; DOWNSTREAM_BASE_URL and the SCHED_/POOL_/SEM_ knobs are set by compose.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
