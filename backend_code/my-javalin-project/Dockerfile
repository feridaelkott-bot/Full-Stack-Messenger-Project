FROM maven:3.9-eclipse-temurin-17
WORKDIR /app
COPY . .
RUN mvn package -DskipTests
EXPOSE 8080
CMD ["java", "-jar", "target/my-javalin-project-1.0-SNAPSHOT.jar"]
