# Builder image
FROM registry.access.redhat.com/ubi9/nodejs-22:latest AS builder

USER 1001
COPY --chown=1001 . .
RUN \
  npm version && \
  npm config ls && \
  npm ci --verbose --ignore-scripts --no-audit && \
  npm run build && \
  npm run dist

# Runner image
FROM registry.access.redhat.com/ubi9/nodejs-22-minimal:latest

# Add ps package to allow liveness probe for k8s cluster
# Add tar package to allow copying files with kubectl scp
USER 0
RUN microdnf -y install tar procps-ng && microdnf clean all

USER 1001

LABEL name="tsd/tsd-ui" \
      description="TSD - User Interface" \
      license="Apache License 2.0" \
      summary="TSD - User Interface" \
      io.k8s.display-name="tsd-ui" \
      io.k8s.description="TSD - User Interface" \
      io.openshift.expose-services="8080:http" \
      io.openshift.tags="tsd,ui,nodejs22" \
      io.openshift.min-cpu="100m" \
      io.openshift.min-memory="350Mi"

COPY --from=builder /opt/app-root/src/dist /opt/app-root/dist/

ENV DEBUG=1

WORKDIR /opt/app-root/dist
ENTRYPOINT ["./entrypoint.sh"]
