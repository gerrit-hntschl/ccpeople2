#!/usr/bin/env bash

set -e

while [[ $# > 1 ]]
do
key="$1"

case $key in
    -p|--password)
    DATOMIC_POSTGRES_PASSWORD="$2"
    shift # past argument
    ;;
    -l|--license)
    DATOMIC_LICENSE_KEY="$2"
    shift # past argument
    ;;
    -j|--jvm_args)
    JVM_ARGS="$2"
    ;;
    *)
            # unknown option
    ;;
esac
shift # past argument or value
done

TRANSACTOR_PROPERTIES_FILE=config/transactor.properties

SAMPLE_TRANSACTOR_PROPERTIES_FILE=config/transactor.properties.sample

cat ${SAMPLE_TRANSACTOR_PROPERTIES_FILE} | sed "s|^license-key=.*|license-key=${DATOMIC_LICENSE_KEY}|" > ${TRANSACTOR_PROPERTIES_FILE}

bin/transactor -Ddatomic.sqlPassword=${DATOMIC_POSTGRES_PASSWORD} $JVM_ARGS -XX:+UseG1GC -XX:MaxGCPauseMillis=50 ${TRANSACTOR_PROPERTIES_FILE}
