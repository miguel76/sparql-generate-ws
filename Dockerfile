FROM maven:3.5.2-jdk-8-alpine AS MAVEN_TOOL_CHAIN
COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn package

FROM openjdk:8-jre-alpine
COPY --from=MAVEN_TOOL_CHAIN /tmp/target/output-jar/sparql-generate-ws.jar sparql-generate-ws.jar
ENTRYPOINT ["java", "-jar", "sparql-generate-ws.jar"]
CMD ["8080"]
