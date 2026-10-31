# Imagem do sistema para a hospedagem (Render, Railway ou qualquer servidor com Docker).
# Duas etapas: a primeira compila com o JDK; a segunda só leva o jar e um Java menor (JRE).

# ---------- 1. Compilação ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY frontend frontend
COPY backend backend
# os testes rodam no seu computador; aqui só empacota
RUN chmod +x mvnw && ./mvnw -B -q -pl backend -am package -DskipTests

# ---------- 2. Execução ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# não roda como root: se alguém invadir a aplicação, tem menos poder no servidor
RUN useradd --system --create-home studio
USER studio

COPY --from=build /app/backend/target/backend-1.0.0.jar app.jar

ENV SPRING_PROFILES_ACTIVE=producao
# servidores rodam em UTC; a agenda precisa do horário de Brasília para saber o que já passou
ENV TZ=America/Sao_Paulo
# cabe nos 512 MB do plano gratuito do Render e acorda mais rápido depois de dormir
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -Duser.timezone=America/Sao_Paulo $JAVA_OPTS -jar app.jar"]
