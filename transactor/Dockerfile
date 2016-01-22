FROM java:8

MAINTAINER Gerrit Hentschel "gerrit.hentschel@gmail.com"

ARG LICENSE
ARG DATOMIC_USER
ARG DATOMIC_PASSWORD

ENV DATOMIC_VERSION 0.9.5344
ENV DATOMIC_HOME /opt/datomic-pro-$DATOMIC_VERSION
ENV DATOMIC_DATA /data


RUN curl -u "${DATOMIC_USER}:${DATOMIC_PASSWORD}" -SL https://my.datomic.com/repo/com/datomic/datomic-pro/$DATOMIC_VERSION/datomic-pro-$DATOMIC_VERSION.zip -o /tmp/datomic.zip \
  && unzip /tmp/datomic.zip -d /opt \
  && rm -f /tmp/datomic.zip

ADD config/transactor.properties.sample /tmp/datomic/config/transactor.properties.sample
RUN cat /tmp/datomic/config/transactor.properties.sample | sed "s|^license-key=.*|license-key=${LICENSE}|" > $DATOMIC_HOME/config/transactor.properties

WORKDIR $DATOMIC_HOME

ENTRYPOINT ["/opt/datomic-pro-0.9.5344/bin/transactor"]

VOLUME $DATOMIC_DATA

EXPOSE 4334

CMD ["config/transactor.properties -Ddatomic.sqlPassword=${DATOMIC_POSTGRES_PASSWORD}"]