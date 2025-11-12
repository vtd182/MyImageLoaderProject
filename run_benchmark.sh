#!/bin/bash

# ImageLoader Real Benchmark Runner
# Automatically runs benchmark test, pulls results, and opens HTML report

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Project directory
PROJECT_DIR="/Users/lap15116/working/ZTF2025/image-loader"
LOCAL_RESULTS_DIR="$PROJECT_DIR/benchmark-results"
DEVICE_RESULTS_DIR="/sdcard/Android/data/com.example.imageloader.test/files/benchmark-results"

echo -e "${BLUE}╔════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   ImageLoader Real Benchmark Runner               ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════╝${NC}"
echo ""

# Step 1: Check device connected
echo -e "${YELLOW}[1/6]${NC} Checking for connected devices..."
DEVICES=$(adb devices | grep -v "List" | grep "device$" | wc -l)

if [ "$DEVICES" -eq 0 ]; then
    echo -e "${RED}❌ No devices connected!${NC}"
    echo "   Please connect a device or start emulator"
    exit 1
fi

DEVICE_NAME=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
echo -e "${GREEN}✓${NC} Device found: ${DEVICE_NAME}"
echo ""

# Step 2: Clean old results on device
echo -e "${YELLOW}[2/6]${NC} Cleaning old results on device..."
adb shell "rm -rf $DEVICE_RESULTS_DIR/*" 2>/dev/null || true
echo -e "${GREEN}✓${NC} Old results cleaned"
echo ""

# Step 3: Build test APK
echo -e "${YELLOW}[3/6]${NC} Building test APK..."
cd "$PROJECT_DIR"
./gradlew :ImageLoader:assembleDebugAndroidTest --quiet || {
    echo -e "${RED}❌ Build failed!${NC}"
    exit 1
}
echo -e "${GREEN}✓${NC} Test APK built successfully"
echo ""

# Step 4: Run benchmark test
echo -e "${YELLOW}[4/6]${NC} Running REAL benchmark test..."
echo -e "${BLUE}   This will take 30-60 seconds...${NC}"
echo -e "${BLUE}   Loading 100 images (3 phases)${NC}"
echo ""

adb shell am instrument -w -e class \
  com.example.imageloader.benchmark.suite.RealCacheBenchmark#testRealCachePerformance \
  com.example.imageloader.test/androidx.test.runner.AndroidJUnitRunner \
  2>&1 | grep -E "(Starting REAL|Phase|Final Statistics|benchmark reports generated|✅|📊|🚀|🔥|📥|📤)" || true

TEST_EXIT_CODE=${PIPESTATUS[0]}

if [ $TEST_EXIT_CODE -ne 0 ]; then
    echo -e "${RED}❌ Test failed with exit code: $TEST_EXIT_CODE${NC}"
    echo "   Check logcat for details: adb logcat | grep 'RealCacheBenchmark'"
    exit 1
fi

echo ""
echo -e "${GREEN}✓${NC} Benchmark test completed successfully!"
echo ""

# Step 5: Pull results from device
echo -e "${YELLOW}[5/6]${NC} Pulling results from device..."

# Create local results directory if not exists
mkdir -p "$LOCAL_RESULTS_DIR"

# Pull all files
adb pull "$DEVICE_RESULTS_DIR" "$LOCAL_RESULTS_DIR/" 2>&1 | grep -v "pulled" || true

# Count files
JSON_COUNT=$(ls -1 "$LOCAL_RESULTS_DIR"/*.json 2>/dev/null | wc -l | tr -d ' ')
CSV_COUNT=$(ls -1 "$LOCAL_RESULTS_DIR"/*.csv 2>/dev/null | wc -l | tr -d ' ')
HTML_COUNT=$(ls -1 "$LOCAL_RESULTS_DIR"/*.html 2>/dev/null | wc -l | tr -d ' ')

echo -e "${GREEN}✓${NC} Results pulled:"
echo "   JSON files: $JSON_COUNT"
echo "   CSV files:  $CSV_COUNT"
echo "   HTML files: $HTML_COUNT"
echo ""

# Step 6: Open HTML report
echo -e "${YELLOW}[6/6]${NC} Opening HTML report..."

# Find the latest HTML file
LATEST_HTML=$(ls -t "$LOCAL_RESULTS_DIR"/benchmark-*.html 2>/dev/null | head -1)

if [ -z "$LATEST_HTML" ]; then
    echo -e "${RED}❌ No HTML report found!${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Latest report: $(basename "$LATEST_HTML")"
echo ""

# Open in browser
open "$LATEST_HTML"

# Show summary from JSON
LATEST_JSON=$(ls -t "$LOCAL_RESULTS_DIR"/benchmark-*.json 2>/dev/null | head -1)

if [ -n "$LATEST_JSON" ]; then
    echo -e "${BLUE}╔════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║   Quick Summary                                    ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    # Extract key metrics using grep and basic text processing
    TOTAL_REQUESTS=$(grep -o '"totalRequests":[0-9]*' "$LATEST_JSON" | head -1 | cut -d':' -f2)
    CACHE_EFFICIENCY=$(grep -o '"cacheEfficiency":[0-9.]*' "$LATEST_JSON" | head -1 | cut -d':' -f2)
    OVERALL_SCORE=$(grep -o '"overallScore":[0-9.]*' "$LATEST_JSON" | head -1 | cut -d':' -f2)
    
    if [ -n "$TOTAL_REQUESTS" ] && [ "$TOTAL_REQUESTS" != "0" ]; then
        echo -e "   ${GREEN}✓${NC} Total Requests: ${TOTAL_REQUESTS}"
        echo -e "   ${GREEN}✓${NC} Cache Efficiency: ${CACHE_EFFICIENCY}%"
        echo -e "   ${GREEN}✓${NC} Overall Score: ${OVERALL_SCORE}/100"
    else
        echo -e "   ${RED}⚠${NC}  Warning: Metrics are zero (no images loaded?)"
    fi
    echo ""
fi

echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✨ Benchmark complete! HTML report opened in browser.${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo ""
echo "📂 Results location: $LOCAL_RESULTS_DIR"
echo ""
echo "🔍 View logcat details:"
echo "   adb logcat | grep RealCacheBenchmark"
echo ""
echo "📊 View JSON:"
echo "   cat $LATEST_JSON | python3 -m json.tool"
echo ""
