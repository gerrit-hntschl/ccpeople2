FROM pointslope/clojure:lein-2.5.0

MAINTAINER Christian Romney "cromney@pointslope.com"

ENV DATOMIC_VERSION 0.9.5344
ENV DATOMIC_HOME /opt/datomic-pro-$DATOMIC_VERSION
ENV DATOMIC_DATA /data

# Datomic Pro Starter as easy as 1-2-3
# 1. Create a .credentials file containing user:pass
# for downloading from my.datomic.com
ADD .credentials /tmp/.credentials

# 2. Make sure to have a config/ folder in the same folder as your
# Dockerfile containing the transactor property file you wish to use
RUN curl -u $(cat /tmp/.credentials) -SL https://my.datomic.com/repo/com/datomic/datomic-pro/$DATOMIC_VERSION/datomic-pro-$DATOMIC_VERSION.zip -o /tmp/datomic.zip \
  && unzip /tmp/datomic.zip -d /opt \
  && rm -f /tmp/datomic.zip

ADD config/transactor.properties.sample $DATOMIC_HOME/config/transactor.properties.sample
ADD start.sh $DATOMIC_HOME/start.sh

WORKDIR $DATOMIC_HOME

ENTRYPOINT ["/opt/datomic-pro-0.9.5344/start.sh"]

# 3. Provide a CMD argument with the relative path to the
# transactor.properties file it will supplement the ENTRYPOINT
VOLUME $DATOMIC_DATA

EXPOSE 4334

CMD ["--password" "sqluserpassword" "--license" "datomic-license"]