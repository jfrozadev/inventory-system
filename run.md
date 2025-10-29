# Running the Inventory Management System

This guide provides comprehensive instructions for running the application using different methods.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Environment Configuration](#environment-configuration)
- [Quick Start with Docker](#quick-start-with-docker)
- [Manual Setup](#manual-setup)
- [Testing the API](#testing-the-api)
- [Monitoring and Observability](#monitoring-and-observability)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Option 1: Docker (Recommended)
- Docker 20.10 or higher
- Docker Compose 2.0 or higher
- 4GB RAM available
- 10GB disk space

### Option 2: Manual Setup
- Java 17 or higher (JDK)
- Maven 3.8 or higher
- PostgreSQL 15
- Redis 7.4
- 2GB RAM available

---

## Environment Configuration

### Understanding the .env.example File

The `.env.example` file contains all necessary environment variables for the application. It includes:

**PostgreSQL Configuration:**
- Primary database for write operations
- Replica database for read operations
- Credentials and connection settings

**Redis Configuration:**
- Cache server host and port
- Used for performance optimization

**Application Settings:**
- Server port configuration
- JVM memory settings
- Grafana admin credentials

### Setting Up Your Environment

1. **Copy the example file:**
```bash
cp .env.example .env
```

2. **Edit the .env file for your environment:**
```bash
nano .env
# or
vim .env
```

3. **Security Best Practices:**
- NEVER commit the `.env` file to version control (already in .gitignore)
- Use strong passwords in production
- Rotate credentials regularly
- Use different credentials for each environment
- Consider using secret management tools (Vault, AWS Secrets Manager, etc.)

**Default Configuration (.env.example):**
```bash
# ============================================
# PostgreSQL Configuration
# ============================================
POSTGRES_PASSWORD=inventory_pass

# ============================================
# Spring Boot Application
# ============================================
SERVER_PORT=8080

# ============================================
# Primary Database (Write Operations)
# ============================================
SPRING_DATASOURCE_PRIMARY_JDBC_URL=jdbc:postgresql://postgres-primary:5432/inventory_db
SPRING_DATASOURCE_PRIMARY_USERNAME=inventory_user
SPRING_DATASOURCE_PRIMARY_PASSWORD=inventory_pass

# ============================================
# Replica Database (Read Operations)
# ============================================
SPRING_DATASOURCE_REPLICA_JDBC_URL=jdbc:postgresql://postgres-replica:5432/inventory_db
SPRING_DATASOURCE_REPLICA_USERNAME=inventory_user
SPRING_DATASOURCE_REPLICA_PASSWORD=inventory_pass

# ============================================
# Redis Cache Configuration
# ============================================
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379

# ============================================
# JVM Configuration
# ============================================
JAVA_OPTS=-Xmx512m -Xms256m

# ============================================
# Grafana Configuration
# ============================================
GF_SECURITY_ADMIN_PASSWORD=admin

# ============================================
# Notes:
# ============================================
# 1. Copy this file to `.env` and update values for production
# 2. Never commit `.env` file to version control
# 3. Use strong passwords in production
# 4. Adjust JVM memory settings based on your infrastructure
```

**Production Configuration Example:**
```bash
# DO NOT USE THESE VALUES - GENERATE YOUR OWN SECURE CREDENTIALS

# PostgreSQL
POSTGRES_PASSWORD=<use-strong-password-here>

# Application
SERVER_PORT=8080

# Primary Database
SPRING_DATASOURCE_PRIMARY_JDBC_URL=jdbc:postgresql://prod-primary-db.example.com:5432/inventory_db
SPRING_DATASOURCE_PRIMARY_USERNAME=<secure-username>
SPRING_DATASOURCE_PRIMARY_PASSWORD=<secure-password>

# Replica Database
SPRING_DATASOURCE_REPLICA_JDBC_URL=jdbc:postgresql://prod-replica-db.example.com:5432/inventory_db
SPRING_DATASOURCE_REPLICA_USERNAME=<secure-username>
SPRING_DATASOURCE_REPLICA_PASSWORD=<secure-password>

# Redis
SPRING_DATA_REDIS_HOST=prod-redis.example.com
SPRING_DATA_REDIS_PORT=6379

# JVM (adjust based on your infrastructure)
JAVA_OPTS=-Xmx2048m -Xms1024m

# Grafana
GF_SECURITY_ADMIN_PASSWORD=<secure-password>
```

---

## Quick Start with Docker

### Method 1: Using Docker Compose (Easiest)

**Step 1: Clone the repository**
```bash
git clone https://github.com/jfrozadev/inventory-system.git
cd inventory-system
```

**Step 2: Setup environment variables**
```bash
# Copy the example file
cp .env.example .env

# Edit with your preferred editor (optional for local dev)
nano .env
```

**Step 3: Start all services**
```bash
docker-compose up -d
```

**Step 4: Check service status**
```bash
docker-compose ps
```

Expected output:
```
NAME                    STATUS              PORTS
inventory-api           running             0.0.0.0:8080->8080/tcp
postgres-primary        running             5432/tcp
postgres-replica        running             5433/tcp
redis                   running             6379/tcp
prometheus              running             9090/tcp
grafana                 running             3000/tcp
```

**Step 5: View logs**
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f inventory-api
```

**Step 6: Access the application**
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Grafana: http://localhost:3000 (use credentials from .env)
- Prometheus: http://localhost:9090

**Stop the services:**
```bash
docker-compose down
```

**Stop and remove volumes (clean restart):**
```bash
docker-compose down -v
```

---

### Method 2: Building Docker Image Manually

**Step 1: Build the application**
```bash
mvn clean package -DskipTests
```

**Step 2: Build Docker image**
```bash
docker build -t inventory-system:latest .
```

**Step 3: Run with Docker (using environment variables)**
```bash
# Load environment variables from .env file
docker run -d \
  --name inventory-api \
  -p 8080:8080 \
  --env-file .env \
  inventory-system:latest
```

**Step 4: Check logs**
```bash
docker logs -f inventory-api
```

**Step 5: Stop and remove**
```bash
docker stop inventory-api
docker rm inventory-api
```

---

## Manual Setup

### Step 1: Install Dependencies

**Install Java 17:**
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk

# macOS (using Homebrew)
brew install openjdk@17

# Verify installation
java -version
```

**Install Maven:**
```bash
# Ubuntu/Debian
sudo apt install maven

# macOS
brew install maven

# Verify installation
mvn -version
```

**Install PostgreSQL:**
```bash
# Ubuntu/Debian
sudo apt install postgresql-15

# macOS
brew install postgresql@15

# Start PostgreSQL
sudo systemctl start postgresql  # Linux
brew services start postgresql@15  # macOS
```

**Install Redis:**
```bash
# Ubuntu/Debian
sudo apt install redis-server

# macOS
brew install redis

# Start Redis
sudo systemctl start redis  # Linux
brew services start redis  # macOS
```

---

### Step 2: Configure Databases

**Create PostgreSQL databases:**
```bash
# Access PostgreSQL
sudo -u postgres psql

# Create user and database (use your own credentials)
CREATE USER inventory_user WITH PASSWORD 'your_secure_password_here';
CREATE DATABASE inventory_db OWNER inventory_user;

# Grant privileges
GRANT ALL PRIVILEGES ON DATABASE inventory_db TO inventory_user;

# Exit
\q
```

**Configure Redis:**
```bash
# Redis runs with default settings on port 6379
# Verify it's running
redis-cli ping
# Expected response: PONG
```

---

### Step 3: Setup Environment Variables

**Create a local .env file:**
```bash
cp .env.example .env
```

**Edit .env for local manual setup:**
```bash
# Update the database hosts for local setup
SPRING_DATASOURCE_PRIMARY_JDBC_URL=jdbc:postgresql://localhost:5432/inventory_db
SPRING_DATASOURCE_PRIMARY_USERNAME=inventory_user
SPRING_DATASOURCE_PRIMARY_PASSWORD=your_secure_password_here

# For local development, use the same database as replica
SPRING_DATASOURCE_REPLICA_JDBC_URL=jdbc:postgresql://localhost:5432/inventory_db
SPRING_DATASOURCE_REPLICA_USERNAME=inventory_user
SPRING_DATASOURCE_REPLICA_PASSWORD=your_secure_password_here

# Redis
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379

# Application
SERVER_PORT=8080
JAVA_OPTS=-Xmx512m -Xms256m
```

**Load environment variables:**
```bash
# Linux/macOS
export $(cat .env | grep -v '^#' | xargs)

# Or use a tool like direnv
# Install direnv: brew install direnv (macOS) or apt install direnv (Ubuntu)
echo 'eval "$(direnv hook bash)"' >> ~/.bashrc
direnv allow .
```

---

### Step 4: Build and Run

**Build the project:**
```bash
mvn clean package
```

**Run the application with environment variables:**
```bash
# Option 1: Run JAR with environment variables from .env
export $(cat .env | grep -v '^#' | xargs)
java -jar target/inventory-system-1.0.0.jar

# Option 2: Run with Maven (environment variables must be exported first)
export $(cat .env | grep -v '^#' | xargs)
mvn spring-boot:run

# Option 3: Pass environment variables inline
SPRING_DATASOURCE_PRIMARY_JDBC_URL=jdbc:postgresql://localhost:5432/inventory_db \
SPRING_DATASOURCE_PRIMARY_USERNAME=inventory_user \
SPRING_DATASOURCE_PRIMARY_PASSWORD=your_password \
java -jar target/inventory-system-1.0.0.jar
```

**Verify the application is running:**
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

---

## Testing the API

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Example 1: Add Inventory (Restock)
```bash
curl -X POST http://localhost:8080/api/v1/inventory/restock \
  -H "Content-Type: application/json" \
  -d '{
    "storeId": "STORE_001",
    "productId": "PROD_001",
    "quantity": 100
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Restock processed successfully",
  "data": {
    "storeId": "STORE_001",
    "productId": "PROD_001",
    "quantity": 100,
    "quantityBefore": 0,
    "cached": false
  },
  "timestamp": "2025-10-29T01:42:36"
}
```

### Example 2: Check Inventory
```bash
curl "http://localhost:8080/api/v1/inventory?storeId=STORE_001&productId=PROD_001"
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Retrieved from cache",
  "data": {
    "storeId": "STORE_001",
    "productId": "PROD_001",
    "quantity": 100,
    "lastUpdated": "2025-10-29T01:42:36",
    "cached": true
  },
  "timestamp": "2025-10-29T01:42:40"
}
```

### Example 3: Process Sale
```bash
curl -X POST http://localhost:8080/api/v1/inventory/sell \
  -H "Content-Type: application/json" \
  -d '{
    "storeId": "STORE_001",
    "productId": "PROD_001",
    "quantity": 5
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Sale processed successfully",
  "data": {
    "storeId": "STORE_001",
    "productId": "PROD_001",
    "quantity": 95,
    "quantityBefore": 100,
    "cached": false
  },
  "timestamp": "2025-10-29T01:42:45"
}
```

### Example 4: Batch Synchronization
```bash
curl -X POST http://localhost:8080/api/v1/inventory/sync \
  -H "Content-Type: application/json" \
  -d '{
    "storeId": "STORE_001",
    "operations": [
      {"productId": "PROD_001", "delta": -10, "type": "SALE"},
      {"productId": "PROD_002", "delta": 50, "type": "RESTOCK"},
      {"productId": "PROD_003", "delta": -5, "type": "SALE"}
    ]
  }'
```

### Complete Test Scenario
```bash
#!/bin/bash
# save as test-flow.sh and run: chmod +x test-flow.sh && ./test-flow.sh

echo "=== Test Scenario: Complete Inventory Flow ==="

echo -e "\n1. Adding 100 units of PROD_001..."
curl -X POST http://localhost:8080/api/v1/inventory/restock \
  -H "Content-Type: application/json" \
  -d '{"storeId": "STORE_001", "productId": "PROD_001", "quantity": 100}'

echo -e "\n\n2. Checking inventory..."
curl "http://localhost:8080/api/v1/inventory?storeId=STORE_001&productId=PROD_001"

echo -e "\n\n3. Selling 30 units..."
curl -X POST http://localhost:8080/api/v1/inventory/sell \
  -H "Content-Type: application/json" \
  -d '{"storeId": "STORE_001", "productId": "PROD_001", "quantity": 30}'

echo -e "\n\n4. Verifying updated inventory..."
curl "http://localhost:8080/api/v1/inventory?storeId=STORE_001&productId=PROD_001"

echo -e "\n\n5. Testing insufficient stock (should fail)..."
curl -X POST http://localhost:8080/api/v1/inventory/sell \
  -H "Content-Type: application/json" \
  -d '{"storeId": "STORE_001", "productId": "PROD_001", "quantity": 1000}'

echo -e "\n\n=== Test Complete ==="
```

---

## Monitoring and Observability

### Access Monitoring Tools

**Grafana Dashboards:**
```bash
# Access Grafana
open http://localhost:3000

# Credentials are in your .env file
# Default: admin / admin (change on first login)
```

**Prometheus Metrics:**
```bash
# Access Prometheus UI
open http://localhost:9090

# Query examples:
# - http_server_requests_seconds_count
# - jvm_memory_used_bytes
# - cache_gets_total
```

### Application Metrics

**List all available metrics:**
```bash
curl http://localhost:8080/actuator/metrics
```

**View specific metrics:**
```bash
# JVM Memory Usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# HTTP Requests
curl http://localhost:8080/actuator/metrics/http.server.requests

# Cache Statistics
curl http://localhost:8080/actuator/metrics/cache.gets

# Database Connection Pool
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

### API Documentation

**Swagger UI:**
```bash
open http://localhost:8080/swagger-ui.html
```

**OpenAPI Specification:**
```bash
curl http://localhost:8080/v3/api-docs
```

---

## Troubleshooting

### Docker Issues

**Problem: Port already in use**
```bash
# Check what's using port 8080
sudo lsof -i :8080

# Kill the process
sudo kill -9 <PID>

# Or change the port in .env file
SERVER_PORT=8081
```

**Problem: Docker containers not starting**
```bash
# Check logs
docker-compose logs

# Restart services
docker-compose restart

# Clean restart
docker-compose down -v
docker-compose up -d
```

**Problem: Out of disk space**
```bash
# Remove unused Docker resources
docker system prune -a --volumes

# Check Docker disk usage
docker system df
```

### Application Issues

**Problem: Application fails to start**
```bash
# Check Java version
java -version  # Must be 17 or higher

# Verify environment variables are loaded
echo $SPRING_DATASOURCE_PRIMARY_JDBC_URL

# Check logs
docker-compose logs inventory-api  # Docker
tail -f logs/application.log  # Manual setup
```

**Problem: Database connection fails**
```bash
# Test PostgreSQL connection
psql -h localhost -U inventory_user -d inventory_db

# Test Redis connection
redis-cli ping

# Verify environment variables
echo $SPRING_DATASOURCE_PRIMARY_JDBC_URL
echo $SPRING_DATA_REDIS_HOST
```

**Problem: Tests failing**
```bash
# Clean and rebuild
mvn clean install

# Run tests with debug
mvn test -X

# Skip tests temporarily
mvn clean package -DskipTests
```

### Security Issues

**Problem: Credentials exposed in logs**
```bash
# Never log sensitive information
# Check your logging configuration
# Ensure DEBUG logging is disabled in production
```

**Problem: .env file committed to git**
```bash
# Remove from git history
git rm --cached .env

# Verify .gitignore includes .env
echo ".env" >> .gitignore

# Commit changes
git add .gitignore
git commit -m "Ensure .env is not tracked"
```

### Performance Issues

**Problem: High memory usage**
```bash
# Adjust JVM settings in .env
JAVA_OPTS=-Xmx1024m -Xms512m

# Restart application
docker-compose restart inventory-api
```

**Problem: Slow response times**
```bash
# Check Redis is running
docker-compose ps redis
redis-cli ping

# Verify cache hit rate
curl http://localhost:8080/actuator/metrics/cache.gets

# Check database connections
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

### Getting Help

**Check application logs:**
```bash
# Docker
docker-compose logs -f inventory-api

# Manual setup
tail -f logs/application.log
```

**Health check endpoints:**
```bash
# Overall health
curl http://localhost:8080/actuator/health

# Detailed health
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/health/liveness
```

---

## Security Best Practices

### Environment Variables
- Never commit `.env` file to version control
- Use different credentials for each environment (dev, staging, prod)
- Rotate credentials regularly
- Use strong, randomly generated passwords

### Production Deployment
- Use secret management tools (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault)
- Enable SSL/TLS for all database connections
- Implement proper authentication (OAuth2/JWT)
- Enable audit logging
- Use firewall rules to restrict database access

### Monitoring
- Set up alerts for failed authentication attempts
- Monitor unusual activity patterns
- Enable audit trail for all operations
- Regular security audits

---

## Additional Resources

- [Architecture Decision Records](./ARCHITECTURE_DECISIONS.md)
- [API Documentation](http://localhost:8080/swagger-ui.html)
- [Grafana Dashboards](http://localhost:3000)
- [Prometheus Metrics](http://localhost:9090)

---

## Quick Reference

### Docker Commands
```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# View logs
docker-compose logs -f

# Restart a service
docker-compose restart inventory-api

# Rebuild and restart
docker-compose up -d --build
```

### Maven Commands
```bash
# Build
mvn clean package

# Run tests
mvn test

# Run application (requires env vars)
mvn spring-boot:run

# Skip tests
mvn clean package -DskipTests
```

### Environment Variables
```bash
# Load from .env file (Linux/macOS)
export $(cat .env | grep -v '^#' | xargs)

# Verify loaded
env | grep SPRING

# Unset all
unset $(cat .env | grep -v '^#' | cut -d= -f1)
```

### Useful Endpoints
- API Base: http://localhost:8080
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics
- Swagger: http://localhost:8080/swagger-ui.html
- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090