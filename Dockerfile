# -------- STAGE 1: Construcción con Gradle --------
FROM gradle:8.5-jdk17 AS build
WORKDIR /workspace

# Copiamos TODO el repo
COPY . .

# Entramos a la carpeta correcta del proyecto
WORKDIR /workspace/proyecto_chat

# Construimos el módulo app
RUN gradle :app:build --no-daemon

# -------- STAGE 2: Construcción Proxy (Node.js) --------
FROM node:18 AS proxy
WORKDIR /proxy

# Copiar solo el código del proxy desde tu estructura real
COPY proyecto_chat/app/src/main/java/proyecto_chat/proxy .

RUN npm install

# -------- STAGE FINAL --------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copiar el JAR generado
COPY --from=build /workspace/proyecto_chat/app/build/libs ./libs

# Copiar proxy ya instalado
COPY --from=proxy /proxy ./proxy

# Copiar script de inicio
COPY start.sh .

RUN chmod +x start.sh

EXPOSE 9090
EXPOSE 10000
EXPOSE 3000

CMD ["bash", "start.sh"]
