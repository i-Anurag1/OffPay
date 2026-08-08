# Build stage
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

RUN apt-get update && apt-get install -y maven

COPY pom.xml .
COPY src src

RUN mvn -q -DskipTests package


# Run stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]