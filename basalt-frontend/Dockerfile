# ─────────────────────────────────────────────────────────────────────────────
# Basalt Frontend — Multi-Stage Docker Build
# Stage 1: Build Angular app with Node
# Stage 2: Serve with Nginx
# ─────────────────────────────────────────────────────────────────────────────

# ── Build Stage ──────────────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app

# Copy package files and install dependencies (layer caching optimization)
COPY package*.json ./
RUN npm ci

# Copy source and build for production
COPY . .
RUN npm run build:prod

# ── Runtime Stage ────────────────────────────────────────────────────────────
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Remove default Nginx static content
RUN rm -rf ./*

# Copy built Angular app from build stage
COPY --from=build /app/dist/basalt-frontend/browser ./

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80
EXPOSE 80

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
