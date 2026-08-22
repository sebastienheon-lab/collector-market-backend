# syntax=docker/dockerfile:1

# --- Build stage -------------------------------------------------------
FROM eclipse-temurin:25-jdk AS compile
WORKDIR /app

# Copy the wrapper and POM first so dependency resolution is cached in its
# own layer, independent of source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM compile AS build
# Spring Boot 4 renamed the layertools jarmode to "tools"; --layers is now opt-in and
# --launcher is required to also extract the spring-boot-loader classes JarLauncher needs.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# --- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]