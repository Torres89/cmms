# Build Stage
FROM maven:3.9.3-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Runtime Stage
FROM amazoncorretto:17-alpine

# Set the working directory in the container
WORKDIR /app

# Copy the compiled Spring Boot application JAR file into the container at /app
COPY --from=build /app/target/*.jar my-spring-boot-app.jar

# Expose the port that the Spring Boot application will run on
EXPOSE 8080

# Cap the heap. Without this the JVM takes 25% of host RAM whether it needs it
# or not, which on a shared multi-tenant box is memory taken from everything
# else. Override with JAVA_OPTS at runtime if a deployment needs more.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:InitialRAMPercentage=25"

# Specify the command to run on container startup
CMD ["sh", "-c", "exec java $JAVA_OPTS --add-opens=java.base/java.lang=ALL-UNNAMED -jar /app/my-spring-boot-app.jar"]
