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
# devices can reach this one directly; the web interface is on 8777.
EXPOSE 41710/udp 8777/tcp
# --ui 0.0.0.0 makes the interface reachable from outside the container: put it
# behind your own access control, or publish it to 127.0.0.1 only
# (-p 127.0.0.1:8777:8777).
ENTRYPOINT ["svoi"]
CMD ["up", "--no-browser", "--ui", "0.0.0.0:8777"]
