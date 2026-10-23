# Imagem usada pela hospedagem (Render ou Railway) para compilar e rodar o sistema.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/ysa-de-paula-studio-*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=producao
# cabe nos 512 MB do plano gratuito do Render e acorda mais rápido depois de dormir
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
