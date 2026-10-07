#!/usr/bin/env bash

rm -rf extracted
rm -rf aot
rm -rf target

mvn -DskipTests -Pnative  clean package

cp target/*jar application.jar

java -Djarmode=tools -jar application.jar extract --destination aot

cd aot

java -XX:AOTCacheOutput=app.aot   -Dspring.aot.enabled=true -Dspring.context.exit=onRefresh -jar application.jar

rm -rf ../application.jar

java -XX:AOTCache=app.aot   -Dspring.aot.enabled=true -jar application.jar
