# sparql-generate-ws

Web service for SPARQL-Generate.

In respect to [sparql-generate/sparql-generate-ws](https://github.com/sparql-generate/sparql-generate-ws), it adds a simple HTTP endpoint at `/transform`.

It has been created to support [AudioCommons/semanticMediator](https://github.com/AudioCommons/semanticMediator).

## Requirements

- Java JDK (version 1.8 or later recommended)
- Maven

## Build

`mvn package`

## Run

`java -jar target/output-jar/sparql-generate-ws.jar <port-number>`
