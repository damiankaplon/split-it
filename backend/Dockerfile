FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre
COPY --from=build /app/build/libs/*-SNAPSHOT.jar /app.jar
COPY docker/entrypoint.sh /entrypoint.sh
# The OpenAI key is a runtime secret, never baked into the image: it is mounted as a Docker
# secret at /run/secrets/OPENAI_API_KEY (or passed as the OPENAI_API_KEY env var).
ENTRYPOINT ["sh", "/entrypoint.sh"]
