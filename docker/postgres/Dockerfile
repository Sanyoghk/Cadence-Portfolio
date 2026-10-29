FROM postgres:17-alpine@sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24
# Same UID/GID/groups/exec contract, without the old Go-based helper and its unused modules.
RUN apk upgrade --no-cache && apk add --no-cache su-exec && rm /usr/local/bin/gosu
# Explicit deletion layer prevents a layer scanner retaining the removed Go binary's metadata.
RUN cp /sbin/su-exec /usr/local/bin/gosu && gosu nobody true
