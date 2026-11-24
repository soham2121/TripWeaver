# TripWeaver API image (Render). Build from the repo root: docker build -f backend/Dockerfile .
# The runtime needs data/sample (town gazetteer, catalogue extracts) and data/curated.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY backend/src src
RUN ./mvnw -q -B package -DskipTests -Dspotless.check.skip=true

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/tripweaver-backend-*.jar app.jar
COPY data/sample data/sample
COPY data/curated data/curated
ENV TRIPWEAVER_CATALOGUE_DATA_DIR=/app/data \
    OSRM_BASE_URL="" \
    DB_POOL_SIZE=3 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8088
CMD ["java", "-jar", "app.jar"]
