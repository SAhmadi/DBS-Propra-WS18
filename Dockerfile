FROM openjdk:11-jdk-slim AS build
WORKDIR /app
COPY . ./
RUN ./gradlew --no-daemon --stacktrace clean shadowJar

FROM openjdk:11-jre-slim
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
RUN mkdir data
CMD java -jar app.jar