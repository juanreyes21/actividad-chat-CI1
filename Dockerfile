# -------- STAGE 1: Build Java app with Gradle --------
FROM gradle:8.5-jdk17 AS build

WORKDIR /workspace

COPY . .

# Build only the app module
RUN gradle :proyecto_chat:app:build --no-daemon || gradle :app:build --no-daemon

# -------- STAGE 2: Build Proxy Node.js --------
FROM node:18 AS proxybuild

WORKDIR /proxy
COPY proyecto_chat/app/src/main/java/proyecto_chat/proxy .

RUN npm install

# -------- STAGE 3: Final container (Java + Node + Supervisor) --------
FROM node:18

RUN apt-get update && apt-get install -y \
    openjdk-17-jre \
    supervisor \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Java app
COPY --from=build /workspace/proyecto_chat/app/build/libs ./libs

# Copy proxy
COPY --from=proxybuild /proxy ./proxy

# Copy start.sh and supervisor config
COPY start.sh .
COPY supervisord.conf .

RUN chmod +x start.sh

# Expose needed ports
EXPOSE 9090
EXPOSE 10000
EXPOSE 3000

CMD ["./start.sh"]
