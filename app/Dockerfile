FROM clojure:latest

VOLUME /usr/src/app
WORKDIR /usr/src/app

COPY project.clj /usr/src/app/

# the credentials will go away as soon as docker-compose supports build-args
ADD .credentials /tmp/.credentials
ADD .artcreds /tmp/.artcreds
RUN export DATOMIC_USER=$(cat /tmp/.credentials | cut -d : -f 1) && export DATOMIC_PASSWORD=$(cat /tmp/.credentials | cut -d : -f 2) && export CCARTUSER=$(cat /tmp/.artcreds | cut -d : -f 1) && export CCARTPASS=$(cat /tmp/.artcreds | cut -d : -f 2) && lein deps

EXPOSE 3449 35001 8000 5005

CMD ["lein", "repl", ":headless", ":port", "35001", ":host", "0.0.0.0"]