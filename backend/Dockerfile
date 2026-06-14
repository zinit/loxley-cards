# syntax=docker/dockerfile:1.7

# === Build stage ===
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN ./mvnw -pl app -am clean package -DskipTests -B

# === Runtime stage ===
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app/target/loxley-cards-app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
