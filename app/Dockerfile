FROM clojure:latest

ARG DATOMIC_USER
ARG DATOMIC_PASSWORD
ARG CCARTUSER
ARG CCARTPASS

VOLUME /usr/src/app
WORKDIR /usr/src/app

COPY project.clj /usr/src/app/

RUN lein deps

EXPOSE 35001 8000 5005

CMD ["lein", "repl", ":headless", ":port", "35001", ":host", "0.0.0.0"]