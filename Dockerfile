# --- Build stage ---
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -q -N io.takari:maven:wrapper 2>/dev/null || true
COPY src src
RUN ./mvnw -q -DskipTests package

# --- Run stage ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
