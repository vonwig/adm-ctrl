FROM babashka/babashka:0.6.5@sha256:66abc2ad36c33bd82537b11e341629a0ead7c119f301c1b8c7865cfc45e61ddd AS bb

FROM openjdk:11-jre-slim-buster@sha256:8bd7a95294e30dff5e21c39d8f6ded9c9dbda1dc331e7cca32ff9568ba83ff9b

RUN apt-get update && \
    apt-get -y --no-install-recommends install openssl curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
# RUN apk add --update openssl && \
    # rm -rf /var/cache/apk/*

USER nobody
WORKDIR /app

COPY --from=bb /usr/local/bin/bb /usr/local/bin
COPY certs.clj /app

ENTRYPOINT ["bb","-f","certs.clj"]
