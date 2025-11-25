#!/bin/bash

echo "=== Iniciando servidores ==="

# Inicia servidor Java
java -jar ./libs/app.jar &

# Inicia proxy Node
node ./proxy/index.js &

wait