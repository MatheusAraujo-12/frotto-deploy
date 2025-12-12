# ========================
# STAGE 1: build da aplicação
# ========================
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copia o wrapper do Maven e as configs
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# (OPCIONAL) se você tiver esse arquivo no projeto, pode copiar:
# COPY sonar-project.properties .

# Cria um arquivo vazio de sonar para não quebrar o plugin
RUN touch sonar-project.properties

# Baixa dependências (cache ajuda nos próximos builds)
RUN ./mvnw -q -DskipTests dependency:resolve dependency:resolve-plugins

# Copia o código fonte e gera o jar
COPY src src

# Aqui o Maven roda todas as fases, incluindo o plugin do sonar,
# mas agora o arquivo existe e ele não quebra mais.
RUN ./mvnw -q -DskipTests package

# ========================
# STAGE 2: imagem final
# ========================
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copia o JAR gerado do stage anterior
COPY --from=build /app/target/*.jar app.jar

# Porta do Spring Boot (conforme seu application.yml)
EXPOSE 5000

# Perfil padrão (ajuste se precisar)
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java","-jar","/app/app.jar"]
