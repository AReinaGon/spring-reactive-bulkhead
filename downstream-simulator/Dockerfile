# Runtime image for the downstream simulator (fake availability + recommendations). Runs a PREBUILT
# jar built with your local Corretto 25:
#
#   mvn -DskipTests package                    # produces target/downstream-simulator-*.jar
#   docker build -t downstream-simulator:local .
FROM amazoncorretto:25

WORKDIR /app

ARG JAR_FILE=target/downstream-simulator-1.0.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 9090

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
