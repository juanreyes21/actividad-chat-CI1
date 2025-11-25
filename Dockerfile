# ----------- STAGE 1: Construcción del proyecto Java con Gradle -----------
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle build --no-daemon

# ----------- STAGE 2: Instalación del proxy Node.js -----------
FROM node:18 AS proxy
WORKDIR /proxy
COPY proyecto_chat/app/src/main/java/proyecto_chat/proxy .
RUN npm install

# ----------- STAGE 3: Imagen final -----------

FROM eclipse-temurin:17-jre

WORKDIR /app

# Copiar la aplicación Java construida
COPY --from=build /app .

# Copiar el proxy Node ya instalado
COPY --from=proxy /proxy ./proxy

# Copiar script de inicio
COPY start.sh .

# Dar permisos de ejecución
RUN chmod +x start.sh

# Exponer puertos que usa tu proyecto
EXPOSE 9090    # servidor principal
EXPOSE 10000   # ICE
EXPOSE 3000    # proxy Node (o el que uses)

# Ejecutar el script que inicia los 3 procesos
CMD ["bash", "start.sh"]
