#!/bin/bash

set -e

if [[ "$(docker images -q app-uberjar-build 2> /dev/null)" == "" ]]; then
    docker build -f Dockerfile-build -t app-uberjar-build .
fi


# Compile Clojurescript and uberjar everything up
docker run -it -v /home/core/share/app:/usr/src/app -v /home/core/.m2:/root/.m2 -e "JIRA_BASE_URL=${JIRA_BASE_URL}" app-uberjar-build
# Create image containing app + java
#docker build -t dockerhub.codecentric.de/ccdashboard-app -f Dockerfile-uberjar .
#docker push dockerhub.codecentric.de/ccdashboard-app
