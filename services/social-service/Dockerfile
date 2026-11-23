FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && sh mvnw -B dependency:go-offline
COPY src ./src
RUN sh mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --create-home --shell /bin/false app && chown -R app:app /app
COPY --from=build --chown=app:app /workspace/target/*.jar app.jar
USER app
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app/app.jar"]
