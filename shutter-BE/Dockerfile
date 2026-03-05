# Stage 1: Build the application using Gradle
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy the Gradle wrapper files and dependencies definition
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Give execution rights to the gradle wrapper
RUN chmod +x gradlew

# Download dependencies (this layer will be cached unless dependencies change)
RUN ./gradlew dependencies --no-daemon

# Copy source code and build the application
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Create a minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built artifact from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose mapping port (matches the compose setup)
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]
