#!/bin/sh
./mvnw -Pnative clean package;
./target/minio-client-demo-1.0.0-SNAPSHOT-runner -Dquarkus.minio.url=http://localhost:9000 -Dquarkus.minio.access-key=minioaccess -Dquarkus.minio.secret-key=miniosecret;
