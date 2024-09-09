# Build jar
FROM maven:3.9.9-eclipse-temurin-22-jammy AS maven
RUN mkdir /application
WORKDIR /application
COPY src ./src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

# Extract jar layers
FROM eclipse-temurin:22-jre-ubi9-minimal AS builder
WORKDIR /builder
COPY --from=maven /application/target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Runtime
FROM eclipse-temurin:22.0.2_9-jre-alpine
WORKDIR /application

COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

RUN addgroup --system appuser \
    && adduser -S -s /usr/sbin/nologin -G appuser appuser

USER appuser

ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","application.jar"]
