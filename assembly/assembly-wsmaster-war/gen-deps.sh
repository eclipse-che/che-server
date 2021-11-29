#!/bin/sh
# Simple script that helps to build local version to test easily
set -e
set -u
cd ../..
mvn clean install
cd  assembly/assembly-wsmaster-war
docker pull  quay.io/che-incubator/dash-licenses:next
docker run --rm -t \
       -v ~/.m2:/root/.m2 \
       -v ${PWD}/:/workspace/project  \
       quay.io/che-incubator/dash-licenses:next --generate
