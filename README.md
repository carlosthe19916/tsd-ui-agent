# TSD UI Agent

Hola Mundo

## Pre requisites

- JDK 25
- Ollama (Local dev)

```shell
curl -fsSL https://ollama.com/install.sh | sh
ollama pull granite3.3:8b
ollama serve
```

## Dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```
