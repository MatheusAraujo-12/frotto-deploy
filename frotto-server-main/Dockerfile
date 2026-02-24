# ========================
# STAGE 1: backend build
# ========================
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Limit Maven memory to reduce build failures on small builders.
ENV MAVEN_OPTS="-Xmx512m -XX:MaxRAMPercentage=75.0"

# Install sed to normalize mvnw line endings from Windows checkouts.
RUN apt-get update && apt-get install -y --no-install-recommends sed \
  && rm -rf /var/lib/apt/lists/*

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN touch sonar-project.properties

# Keep Maven logs visible so CI shows the real failing step.
RUN ./mvnw -B -ntp -DskipTests dependency:resolve dependency:resolve-plugins

COPY src src

RUN ./mvnw -B -ntp -DskipTests package

# ========================
# STAGE 2: runtime image
# ========================
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 5000
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java","-jar","/app/app.jar"]
