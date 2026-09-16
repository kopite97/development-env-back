# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle/ ./gradle/
# Support checkouts with Windows line endings.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY src/main/ ./src/main/

# Preserve the existing safe migration stage; see docs/project-category-only-rollout.md.
ARG CATEGORY_STAGE=bridge
RUN --mount=type=cache,target=/root/.gradle \
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
