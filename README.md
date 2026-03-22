# TSD UI Agent

## Pre requisites

- JDK 25
- Ollama (Local dev)

```shell
curl -fsSL https://ollama.com/install.sh | sh
ollama pull granite3.3:8b
ollama serve
```

### Kubernetes Mode

- Start Minikube:

```shell
minikube start --addons=ingress,dashboard --memory=10240 --cpus=4 --disk-size=50GB
```

- Install `chectl` if you don't have it. See https://github.com/che-incubator/chectl#installation

```shell
chectl server:deploy --platform minikube
```

- IMPORTANT: Patch the CheCluster to fix the UID mismatch. The default runAsUser (1234) doesn't exist in most devfile
  container images. UID 1001 matches the "default" user in Red Hat UBI-based images.

```shell
kubectl patch checluster eclipse-che -n eclipse-che --type=merge -p '                                                                                                                                             
{
  "spec": {
    "devEnvironments": {
      "maxNumberOfRunningWorkspacesPerUser": -1,
      "defaultComponents": [
        {
          "name": "universal-developer-image",
          "container": {
            "image": "quay.io/devfile/universal-developer-image:ubi8-latest"
          }
        }
      ]
    }
  }
}'
```

- Verify:

```shell
chectl server:status
chectl dashboard:open
```

## Dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```
