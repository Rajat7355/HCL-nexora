FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY kit/pom.xml kit/
COPY catalog/pom.xml catalog/
COPY commerce/pom.xml commerce/
COPY knowledge/pom.xml knowledge/
COPY assistant/pom.xml assistant/
COPY app/pom.xml app/
RUN mvn -q -B dependency:go-offline -DskipTests || true
COPY . .
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/app/target/app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
