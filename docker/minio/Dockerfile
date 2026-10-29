# Local/integration fixture only. The archived community server is not a production deployment.
# Official security release 2025-10-15, verified against upstream release commit and source hash.
FROM golang:1.27.3-alpine@sha256:d200d2dd76b03a99b52651fd1325aff7dcd35422c35c01bac56571b0507f3deb AS server-build
ENV CGO_ENABLED=0 GOTOOLCHAIN=local
ADD --checksum=sha256:45521908307306e925c98d629e1c17d78c8b72b6ee242b1bfb1409f7d8ee5841 https://codeload.github.com/minio/minio/tar.gz/9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a /tmp/source.tar.gz
RUN mkdir /src && tar -xzf /tmp/source.tar.gz -C /src --strip-components=1
WORKDIR /src
COPY server.go.mod ./go.mod
COPY server.go.sum ./go.sum
RUN go build -mod=readonly -trimpath -ldflags='-s -w' -o /out/minio . \
    && go list -mod=readonly -deps . > /out/server-packages.txt

FROM golang:1.27.3-alpine@sha256:d200d2dd76b03a99b52651fd1325aff7dcd35422c35c01bac56571b0507f3deb AS client-build
ENV CGO_ENABLED=0 GOTOOLCHAIN=local
ADD --checksum=sha256:95cd293c7119f16921a6dc515a1fb74a2227f19fd994b9c8b770a154e802ac44 https://codeload.github.com/minio/mc/tar.gz/7394ce0dd2a80935aded936b09fa12cbb3cb8096 /tmp/source.tar.gz
RUN mkdir /src && tar -xzf /tmp/source.tar.gz -C /src --strip-components=1
WORKDIR /src
COPY client.go.mod ./go.mod
COPY client.go.sum ./go.sum
RUN go build -mod=readonly -trimpath -ldflags='-s -w' -o /out/mc . \
    && go list -mod=readonly -deps . > /out/client-packages.txt

FROM alpine:3.24.0@sha256:a2d49ea686c2adfe3c992e47dc3b5e7fa6e6b5055609400dc2acaeb241c829f4
COPY --from=server-build /out/minio /usr/local/bin/minio
COPY --from=client-build /out/mc /usr/local/bin/mc
COPY --from=server-build /out/server-packages.txt /usr/local/share/cadence-minio/server-packages.txt
COPY --from=client-build /out/client-packages.txt /usr/local/share/cadence-minio/client-packages.txt
COPY --from=server-build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
RUN apk upgrade --no-cache \
    && addgroup -g 10001 evidence && adduser -D -u 10001 -G evidence evidence && mkdir /data && chown evidence:evidence /data
USER 10001:10001
ENV MINIO_UPDATE=off
EXPOSE 9000
ENTRYPOINT ["/usr/local/bin/minio"]
CMD ["server", "/data", "--address", ":9000"]
