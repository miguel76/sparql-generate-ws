# sparql-generate-ws

Web service for SPARQL-Generate.

In respect to [sparql-generate/sparql-generate-ws](https://github.com/sparql-generate/sparql-generate-ws), it adds a simple HTTP endpoint at `/transform`.

It has been created to support [AudioCommons/semanticMediator](https://github.com/AudioCommons/semanticMediator).

__Note__: in commands and URLs below the port 5050 is always used, but you are free to use the port that suits you.

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
docker run -d -p 127.0.0.1:5050:8080/tcp --name sparql-generate miguel76/sparql-generate
```

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
http://localhost:5050/transform

- Parameters
  - __query__: SPARQL-Generate query
  
    _Example:_
    ```
    PREFIX iter: <http://w3id.org/sparql-generate/iter/>
    GENERATE {?book a <http://schema.org/Book>}
    ITERATOR iter:JSONPath(?source, "$.books.[*]") AS ?book
    WHERE {}
    ```
  - __bindings__: initial variable bindings, as JSON
  
    _Example:_
    ```
    {
      "source": "{ \"books\": [ \"ModyDick\"] }"
    }
    ```

## Run Locally

### Requirements

- Java JDK (version 1.8 or later recommended)
- Maven

### Build

```
mvn package
```

### Run

```
java -jar target/output-jar/sparql-generate-ws.jar 5050
```
