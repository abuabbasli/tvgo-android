# Build stage
FROM node:20-slim AS build

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm install

# Copy source and build
COPY . .

# Build with API URL pointing to backend
ARG VITE_API_URL=https://hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws/api
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# Production stage with AWS Lambda Web Adapter
FROM public.ecr.aws/awsguru/aws-lambda-adapter:0.8.4 AS lambda-adapter

FROM nginx:alpine

# Copy Lambda Web Adapter
COPY --from=lambda-adapter /lambda-adapter /opt/extensions/lambda-adapter

# Set Lambda Web Adapter environment variables
# Use buffered mode (not streaming) for static files
ENV AWS_LWA_PORT=8080
ENV AWS_LWA_READINESS_CHECK_PATH=/
ENV AWS_LWA_INVOKE_MODE=buffered
ENV AWS_LWA_REMOVE_BASE_PATH=""

# Create necessary directories in /tmp for Lambda read-only filesystem
RUN mkdir -p /tmp/client_temp /tmp/proxy_temp /tmp/fastcgi_temp /tmp/uwsgi_temp /tmp/scgi_temp && \
    chmod -R 777 /tmp

# Remove default nginx config
RUN rm -f /etc/nginx/conf.d/default.conf

# Copy built assets
COPY --from=build /app/dist /usr/share/nginx/html

# Copy nginx config
COPY nginx.conf /etc/nginx/nginx.conf

# Expose port 8080
EXPOSE 8080

# Run nginx in foreground
CMD ["nginx", "-g", "daemon off;"]
