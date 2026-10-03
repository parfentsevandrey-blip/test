# syntax=docker/dockerfile:1
FROM golang:1.26 AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" -o /out/svoi ./cmd/svoi
# The data directory, created here because the final image has no shell: it must
# belong to the unprivileged user, or a fresh volume (a copy of this directory)
# would be root's and svoi could not write its key.
RUN mkdir -p /out/data && chmod 700 /out/data

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/svoi /usr/local/bin/svoi
COPY --from=build --chown=65532:65532 /out/data /data
# State (device key, mesh certificates, mail, config) lives here — keep it in a volume.
ENV SVOI_DIR=/data
VOLUME /data
# Mesh traffic. Publish it (-p 41710:41710/udp, or --network host) so that other
# devices can reach this one directly.
EXPOSE 41710/udp
# The web interface listens on the container's loopback only (the sign-in link and the
# session cookie travel in clear text over HTTP, so it must not be reachable by whoever
# shares the network). Reach it with `docker exec <container> svoi url` (a fresh one-time
# link) + an SSH tunnel / `docker run --network host`, or put your own TLS reverse proxy
# in front and start with `svoi up --ui 0.0.0.0:8777` knowing that this switches off the
# Host-header check.
ENTRYPOINT ["svoi"]
CMD ["up", "--no-browser", "--ui", "127.0.0.1:8777"]
