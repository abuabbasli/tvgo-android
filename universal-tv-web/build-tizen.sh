#!/bin/bash

# Build script for deploying universal-tv-web to Tizen app
# Usage: ./build-tizen.sh [--api-url <url>]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TIZEN_APP_DIR="$(dirname "$SCRIPT_DIR")/tizen-app"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Building universal-tv-web for Tizen  ${NC}"
echo -e "${GREEN}========================================${NC}"

# Parse arguments
API_URL=""
while [[ $# -gt 0 ]]; do
    case $1 in
        --api-url)
            API_URL="$2"
            shift 2
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Step 1: Install dependencies if needed
echo -e "\n${YELLOW}Step 1: Checking dependencies...${NC}"
if [ ! -d "node_modules" ]; then
    echo "Installing npm dependencies..."
    npm install
else
    echo "Dependencies already installed."
fi

# Set default API URL if not provided
if [ -z "$API_URL" ]; then
    API_URL="http://localhost:8000"
fi

# Step 2: Build the React app
echo -e "\n${YELLOW}Step 2: Building the web app...${NC}"
echo "Using API URL: $API_URL"
VITE_API_URL="$API_URL" npm run build

# Step 3: Verify build output
echo -e "\n${YELLOW}Step 3: Verifying build...${NC}"
if [ ! -d "dist" ]; then
    echo -e "${RED}Error: Build failed - dist directory not found${NC}"
    exit 1
fi

if [ ! -f "dist/index.html" ]; then
    echo -e "${RED}Error: Build failed - index.html not found in dist${NC}"
    exit 1
fi

echo -e "${GREEN}Build successful!${NC}"

# Step 4: Copy to Tizen app
echo -e "\n${YELLOW}Step 4: Copying build to tizen-app...${NC}"

if [ ! -d "$TIZEN_APP_DIR" ]; then
    echo -e "${RED}Error: tizen-app directory not found at $TIZEN_APP_DIR${NC}"
    exit 1
fi

# Clean old assets (but preserve config.xml, icon.png, etc.)
echo "Cleaning old build files..."
rm -rf "$TIZEN_APP_DIR/assets"
rm -f "$TIZEN_APP_DIR/index.html"

# Copy new build
echo "Copying new build files..."
cp -r dist/* "$TIZEN_APP_DIR/"

echo -e "${GREEN}Files copied successfully!${NC}"

# Step 5: Summary
echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}           Build Complete!             ${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\nBuild output copied to: ${YELLOW}$TIZEN_APP_DIR${NC}"
echo -e "\nNext steps:"
echo -e "  1. Install tizen extension for vs code"
echo -e "  2. right click of tizen-app folder and select run as tizen TV Web app project"
