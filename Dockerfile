# ---------- Build stage ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /workspace

# copy only maven files first for better cache usage
COPY pom.xml ./

# optional maven settings (uncomment if you have settings.xml in project root)
# COPY settings.xml /workspace/

RUN mvn -q -DskipTests dependency:go-offline

# copy source and build
COPY src ./src
RUN mvn -B -DskipTests package

# ---------- Run stage ----------
FROM eclipse-temurin:17-jre-jammy

ARG APP_USER=appuser
RUN groupadd -r  && useradd -r -g  -d /home/ -m 

WORKDIR /app

# Copy the built jar (pattern covers SNAPSHOT naming)
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

ENV TZ=UTC
ENV LANG=C.UTF-8
ENV JAVA_OPTS="-Xms256m -Xmx512m -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://localhost:8080/ || exit 1

USER appuser

ENTRYPOINT ["sh", "-c", "exec java  -jar /app/app.jar"]


