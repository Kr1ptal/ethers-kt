#!/bin/bash

# Script to publish and deploy to Maven Central in one command
# This script runs both the publish and jreleaserDeploy tasks sequentially

set -e  # Exit on any error

echo "🚀 Starting Maven Central deployment process..."
echo ""

# Check if required environment variables are set
if [[ -z "$MAVEN_CENTRAL_USERNAME" ]]; then
    echo "❌ Error: MAVEN_CENTRAL_USERNAME environment variable is not set"
    exit 1
fi

if [[ -z "$MAVEN_CENTRAL_PASSWORD" ]]; then
    echo "❌ Error: MAVEN_CENTRAL_PASSWORD environment variable is not set"
    exit 1
fi

if [[ -z "$JRELEASER_GPG_PUBLIC_KEY" ]]; then
    echo "❌ Error: $JRELEASER_GPG_PUBLIC_KEY environment variable is not set"
    exit 1
fi

if [[ -z "$JRELEASER_GPG_SECRET_KEY" ]]; then
    echo "❌ Error: $JRELEASER_GPG_SECRET_KEY environment variable is not set"
    exit 1
fi

if [[ -z "$JRELEASER_GPG_PASSPHRASE" ]]; then
    echo "❌ Error: JRELEASER_GPG_PASSPHRASE environment variable is not set"
    exit 1
fi

echo "✅ Environment variables are configured"
echo ""

# Step 1: Publish artifacts to staging directory
echo "📦 Step 1: Publishing artifacts to staging directory..."
./gradlew publish

if [[ $? -ne 0 ]]; then
    echo "❌ Failed to publish artifacts"
    exit 1
fi

echo "✅ Artifacts published successfully!"
echo ""

# Step 2: Deploy to Maven Central using JReleaser
echo "🚀 Step 2: Deploying to Maven Central using JReleaser..."
./gradlew jreleaserDeploy

if [[ $? -ne 0 ]]; then
    echo "❌ Failed to deploy to Maven Central"
    exit 1
fi

echo ""
echo "🎉 Successfully deployed to Maven Central!"
echo "📦 All artifacts have been published and deployed"