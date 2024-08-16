# Use the official OpenJDK image as a base image
FROM openjdk:17-jdk-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy the packaged jar file into the container
COPY target/*.jar app.jar

# Expose the port that your Spring Boot app will run on
EXPOSE 8080

# Command to run the jar file
ENTRYPOINT ["java","-jar","/app.jar"]
