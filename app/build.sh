#!/bin/bash

set -e

# Compile Clojurescript and uberjar everything up
docker run -it -v /home/core/share/app:/usr/src/app -v /home/core/.m2:/root/.m2 app-uberjar-build
# Create image containing app + java
docker build -t dockerhub.codecentric.de/ccdashboard-app -f Dockerfile-uberjar .
docker push dockerhub.codecentric.de/ccdashboard-app
