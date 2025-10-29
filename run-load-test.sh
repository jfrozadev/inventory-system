#!/bin/bash

set -e

API_URL="http://localhost:8080"
PROMETHEUS_URL="http://localhost:9090"
GRAFANA_URL="http://localhost:3000"

echo "=========================================="
echo " Load Test - Distributed Inventory API"
echo "=========================================="
echo ""
echo " Monitoring URLs:"
echo "   - API:        $API_URL"
echo "   - Prometheus: $PROMETHEUS_URL"
echo "   - Grafana:    $GRAFANA_URL"
echo "   - Swagger:    $API_URL/swagger-ui.html"
echo ""

if ! command -v hey &> /dev/null; then
    echo " 'hey' not found. Installing..."
    echo ""
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        wget https://hey-release.s3.us-east-2.amazonaws.com/hey_linux_amd64
        chmod +x hey_linux_amd64
        sudo mv hey_linux_amd64 /usr/local/bin/hey
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        brew install hey
    else
        echo " Unsupported OS. Install 'hey' manually: https://github.com/rakyll/hey"
        exit 1
    fi
fi

echo " Checking API health..."
if ! curl -s -f "$API_URL/actuator/health" > /dev/null; then
    echo "  API is not responding. Please start the application:"
    echo "   docker-compose up -d"
    exit 1
fi
echo "  API is UP"
echo ""

STORE_ID="STORE_001"
PRODUCT_IDS=("PROD_0001" "PROD_0002" "PROD_0003" "PROD_0004" "PROD_0005")

echo "  Initializing test data..."
for PRODUCT_ID in "${PRODUCT_IDS[@]}"; do
    curl -s -X POST "$API_URL/api/v1/inventory/restock" \
        -H "Content-Type: application/json" \
        -d "{
            \"storeId\": \"$STORE_ID\",
            \"productId\": \"$PRODUCT_ID\",
            \"quantity\": 10000
        }" > /dev/null
    echo "   ✓ $PRODUCT_ID initialized with 10,000 units"
done
echo ""

echo " Starting Load Test..."
echo "=========================================="
echo ""

run_test() {
    local TEST_NAME=$1
    local METHOD=$2
    local ENDPOINT=$3
    local DURATION=$4
    local CONCURRENCY=$5
    local QPS=$6
    local BODY=$7

    echo " Test: $TEST_NAME"
    echo "   Duration: ${DURATION}s | Concurrency: $CONCURRENCY | Rate: $QPS req/s"

    if [ -z "$BODY" ]; then
        hey -z ${DURATION}s -c $CONCURRENCY -q $QPS -m $METHOD "$API_URL$ENDPOINT"
    else
        hey -z ${DURATION}s -c $CONCURRENCY -q $QPS -m $METHOD \
            -H "Content-Type: application/json" \
            -d "$BODY" \
            "$API_URL$ENDPOINT"
    fi

    echo ""
    sleep 5
}

echo "  Test 1: GET /inventory (Cache Performance)"
run_test "Read-Heavy Workload" "GET" "/api/v1/inventory?storeId=$STORE_ID&productId=PROD_0001" 30 50 100

echo "  Test 2: POST /sell (Write Performance)"
SELL_BODY="{\"storeId\":\"$STORE_ID\",\"productId\":\"PROD_0002\",\"quantity\":1}"
run_test "Sell Operations" "POST" "/api/v1/inventory/sell" 30 50 50 "$SELL_BODY"

echo "  Test 3: POST /restock (Write Performance)"
RESTOCK_BODY="{\"storeId\":\"$STORE_ID\",\"productId\":\"PROD_0003\",\"quantity\":100}"
run_test "Restock Operations" "POST" "/api/v1/inventory/restock" 30 50 30 "$RESTOCK_BODY"

echo "  Test 4: Mixed Workload (70% READ / 30% WRITE)"
echo "   Running GET requests..."
hey -z 60s -c 70 -q 70 -m GET "$API_URL/api/v1/inventory?storeId=$STORE_ID&productId=PROD_0004" > /dev/null 2>&1 &
PID_GET=$!

echo "   Running POST requests..."
MIXED_BODY="{\"storeId\":\"$STORE_ID\",\"productId\":\"PROD_0005\",\"quantity\":1}"
hey -z 60s -c 30 -q 30 -m POST \
    -H "Content-Type: application/json" \
    -d "$MIXED_BODY" \
    "$API_URL/api/v1/inventory/sell" > /dev/null 2>&1 &
PID_POST=$!

wait $PID_GET
wait $PID_POST
echo "   ✓ Mixed workload completed"
echo ""

echo "  Test 5: Stress Test (Burst Traffic)"
run_test "Burst Traffic" "GET" "/api/v1/inventory?storeId=$STORE_ID&productId=PROD_0001" 15 200 200

echo "=========================================="
echo "  Load Test Completed!"
echo "=========================================="
echo ""
echo "  View Results:"
echo "   - Prometheus: $PROMETHEUS_URL/graph"
echo "   - Grafana:    $GRAFANA_URL"
echo "   - API Logs:   docker logs inventory-api -f"
echo ""
echo "  Metrics to check:"
echo "   - http_server_requests_seconds_count"
echo "   - http_server_requests_seconds_sum"
echo "   - resilience4j_circuitbreaker_state"
echo "   - jvm_memory_used_bytes"
echo ""
