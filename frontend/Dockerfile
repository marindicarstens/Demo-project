# Multi-stage build: compiles the Angular app, then serves it from a minimal Nginx image.

# --- Build stage ---
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# --- Runtime stage ---
# 1.27-alpine was frozen 17 months back (stale Alpine OS packages - several HIGH/CRITICAL CVEs
# in libpng/openssl/libxml2/musl/nghttp2/zlib, found by the Trivy image scan wired up in CI);
# 1.31-alpine is the actively-patched current minor line.
FROM nginx:1.31-alpine AS runtime
# The base image can lag behind Alpine's security fixes (libexpat CVE-2026-93990 was fixed in
# Alpine before nginx:1.31-alpine was rebuilt), so apply them at build time.
RUN apk upgrade --no-cache
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# 127.0.0.1, not localhost: Alpine's wget resolves "localhost" to ::1 first, and nginx here
# only listens on the IPv4 wildcard, so "localhost" fails with connection refused.
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
    CMD wget -q --spider http://127.0.0.1/ || exit 1

EXPOSE 80
