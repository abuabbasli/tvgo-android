# Build Stage
FROM node:20-alpine as build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .

# Build arguments for platform and API URL
ARG VITE_PLATFORM=web
ARG VITE_API_URL=http://backend:8000
ENV VITE_PLATFORM=$VITE_PLATFORM
ENV VITE_API_URL=$VITE_API_URL

RUN npm run build

# Production Stage (Nginx)
FROM nginx:alpine

# Copy built files
COPY --from=build /app/dist /usr/share/nginx/html

# Copy nginx config for API proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
