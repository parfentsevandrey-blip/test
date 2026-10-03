# syntax=docker/dockerfile:1
FROM golang:1.26 AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" -o /out/svoi ./cmd/svoi

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/svoi /usr/local/bin/svoi
# State (device key, mesh certificates, mail, config) lives here — keep it in a volume.
ENV SVOI_DIR=/data
VOLUME /data
# Mesh traffic. Publish it (-p 41710:41710/udp, or --network host) so that other
# devices can reach this one directly.
EXPOSE 41710/udp
# The web interface listens on the container's loopback only (the token travels in
# clear text over HTTP, so it must not be reachable by whoever shares the network).
# Reach it with `docker exec <container> svoi url` + an SSH tunnel / `docker run
# --network host`, or put your own TLS reverse proxy in front and start with
# `svoi up --ui 0.0.0.0:8777` knowing that this switches off the Host-header check.
ENTRYPOINT ["svoi"]
CMD ["up", "--no-browser", "--ui", "127.0.0.1:8777"]
