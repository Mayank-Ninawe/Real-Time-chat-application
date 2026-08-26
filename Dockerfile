# Multi-stage Dockerfile for Java TCP Chat Server

# Stage 1: Build stage using Maven and Temurin JDK 17
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy POM and source files
COPY pom.xml .
COPY src ./src

# Package application into runnable fat JARs
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage using lightweight Temurin JRE 17 Alpine
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Copy compiled chat-server.jar from build stage
COPY --from=build /app/target/chat-server.jar ./chat-server.jar

# Fallback default port (overridden dynamically by Render via $PORT)
ENV PORT=12345
EXPOSE 12345

# Execute chat server JAR
ENTRYPOINT ["java", "-jar", "chat-server.jar"]
