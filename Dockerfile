FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build
RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 dos2unix \
    && rm -rf /var/lib/apt/lists/*
COPY . .
ENV TAMMY_BUILD_FLAVOR=PROD
ENV NODE_OPTIONS="--max-old-space-size=4096"
RUN dos2unix gradlew && ./gradlew packageReleaseWebZip

FROM nginx:alpine
COPY --from=builder /build/build/dist/composeWebCompatibility/productionExecutable/ /usr/share/nginx/html/
RUN cat > /etc/nginx/conf.d/default.conf <<'EOF'
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # Ensure SPA shell is always refreshed after deployments.
    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        add_header Pragma "no-cache" always;
        add_header Expires "0" always;
    }

    # Never rewrite missing static files to index.html.
    # This avoids white pages when stale cached bundles request removed chunks.
    location ~* \.(js|css|wasm|map|json|ico|png|jpg|jpeg|gif|svg|webp|woff2?)$ {
        try_files $uri =404;
        add_header Cache-Control "no-cache, must-revalidate" always;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
EOF
