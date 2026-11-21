FROM maven:3.9.11-eclipse-temurin-8 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY examples ./examples
COPY transporter_assignment_data.csv ./
RUN mvn --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --create-home app \
    && mkdir -p /app/data \
    && chown -R app:app /app
COPY --from=build --chown=app:app /build/target/transporter-assignment.jar /app/app.jar
ENV DATA_DIR=/app/data
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:InitialRAMPercentage=20.0"
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
