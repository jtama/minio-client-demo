#!/bin/sh
docker container run -d -e MINIO_ACCESS_KEY=minioaccess -e MINIO_SECRET_KEY=miniosecret -p 9000:9000 -p 9001:9001 --name no-devservices minio/minio:RELEASE.2022-10-08T20-11-00Z server /data --console-address :9001
