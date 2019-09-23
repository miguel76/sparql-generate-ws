# sparql-generate-ws

Web service for SPARQL-Generate.

In respect to [sparql-generate/sparql-generate-ws](https://github.com/sparql-generate/sparql-generate-ws), it adds a simple HTTP endpoint at `/transform`.

It has been created to support [AudioCommons/semanticMediator](https://github.com/AudioCommons/semanticMediator).

## Run with Docker (recommended)

### Requirements

- Docker

### Build and launch container

Download image
```
docker pull miguel76/sparql-generate
```

Build container and launch it for the first time
```
docker run -d -p 127.0.0.1:<port-number>:8080/tcp --name sparql-generate miguel76/sparql-generate
```

### Open documentation

Open http://localhost:<port-number>/transform

### Container management

Stop container
```
docker stop sparql-generate
```
Start container
```
docker start sparql-generate
```

### API

- Endpoint
http://localhost:<port-number>/transform

- Parameters
  - query: SPARQL-Generate query
  - bindings: intial variable bindings


## Run Locally

### Requirements

- Java JDK (version 1.8 or later recommended)
- Maven

### Build

`mvn package`

### Run

`java -jar target/output-jar/sparql-generate-ws.jar <port-number>`
