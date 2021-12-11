#!/bin/bash

cp template data -r
./download_plugins.sh
./update.sh
sudo chmod 400 mongo-keyfile
chown 999:999 mongo-keyfile
docker-compose build
