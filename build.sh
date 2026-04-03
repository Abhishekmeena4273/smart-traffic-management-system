#!/bin/bash
# Build script for Render
# Install Java 17
apt-get update
apt-get install -y openjdk-17-jdk

# Set JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Verify Java
java -version

# Build the project
./mvnw package -DskipTests
