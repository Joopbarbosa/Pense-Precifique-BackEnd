FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY .mvn ./.mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# OP #418 — Semgrep (missing-user-entrypoint): sem USER o processo roda como root no
# container. Cria usuário/grupo de sistema dedicado, sem privilégio de login/shell.
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app

COPY --from=build /app/target/*.jar app.jar
RUN chown app:app app.jar

USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
