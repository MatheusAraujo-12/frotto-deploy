# ========================
# STAGE 1: build da aplicação
# ========================
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Instala sed (para corrigir CRLF do mvnw) - leve e garante compatibilidade
RUN apt-get update && apt-get install -y --no-install-recommends sed \
  && rm -rf /var/lib/apt/lists/*

# Copia o wrapper do Maven e as configs
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Corrige CRLF (Windows) e garante permissão de execução
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Cria um arquivo vazio de sonar para não quebrar o plugin
RUN touch sonar-project.properties

# Baixa dependências (cache ajuda nos próximos builds)
RUN ./mvnw -q -DskipTests dependency:resolve dependency:resolve-plugins

# Copia o código fonte e gera o jar
COPY src src

# Aqui o Maven roda todas as fases
RUN ./mvnw -q -DskipTests package

# ========================
# STAGE 2: imagem final
# ========================
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copia o JAR gerado do stage anterior
COPY --from=build /app/target/*.jar app.jar

EXPOSE 5000
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java","-jar","/app/app.jar"]
