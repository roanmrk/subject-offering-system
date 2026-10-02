# ============================================================
# Stage 1: Build the React Frontend
# ============================================================
FROM node:18-alpine AS frontend-build

WORKDIR /frontend

# Copy package files first (better layer caching)
COPY frontend/package.json frontend/package-lock.json ./

# Install dependencies (npm ci uses the lockfile exactly)
RUN npm ci --legacy-peer-deps

# Copy frontend source
COPY frontend/ ./

# Build the React app (outputs to /frontend/build)
RUN npm run build


# ============================================================
# Stage 2: Build the Spring Boot Backend (with frontend embedded)
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS backend-build

WORKDIR /app

# Copy backend pom.xml and source
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src

# Copy the React build into Spring Boot's static resources
# so it gets bundled into the JAR
COPY --from=frontend-build /frontend/build ./src/main/resources/static

# Build the JAR
RUN mvn clean package -DskipTests


# ============================================================
# Stage 3: Run the Application (lightweight runtime)
# ============================================================
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built JAR
COPY --from=backend-build /app/target/*.jar app.jar

# Expose port (Render will override with $PORT env var)
EXPOSE 8080

# Run the application.
# NOTE: Do NOT pass -Dserver.port=${PORT:8080} here — the exec-form
# ENTRYPOINT does not expand shell variables. Spring Boot already
# reads server.port from application.properties which uses ${PORT:8080}.
ENTRYPOINT ["java", "-jar", "app.jar"]