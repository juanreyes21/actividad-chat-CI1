#!/bin/bash

echo "Iniciando ICE server..."
./gradlew :app:runIceServer &

echo "Iniciando Chat server..."
./gradlew :app:runServer &

echo "Iniciando proxy Node..."
cd proxy && npm start &

# Mantener los procesos vivos
wait
