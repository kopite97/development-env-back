# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle/ ./gradle/
# Support checkouts with Windows line endings.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY src/main/ ./src/main/

# Explicit opt-in; this source no longer builds bridge artifacts. No database is touched here.
ARG CATEGORY_STAGE
RUN --mount=type=cache,target=/root/.gradle \
    test "${CATEGORY_STAGE}" = final || { echo 'This release requires --build-arg CATEGORY_STAGE=final; use the preserved artifact for bridge.' >&2; exit 1; }; \
    ./gradlew --no-daemon bootJar -PcategoryStage=${CATEGORY_STAGE} && \
    cp build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --gid 10001 app && \
    useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar ./app.jar
USER app:app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
