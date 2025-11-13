#!/bin/bash

# ImageLoader MacroBenchmark Runner
# Runs REAL RecyclerView benchmark test and pulls results

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
echo -e "${BLUE}║   ImageLoader MacroBenchmark Runner               ║${NC}"
echo -e "${BLUE}║   (Real RecyclerView + Scroll Test)                ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════╝${NC}"
echo ""

# Step 1: Check device connected
echo -e "${YELLOW}[1/5]${NC} Checking for connected devices..."
DEVICES=$(adb devices | grep -v "List" | grep "device$" | wc -l)

if [ "$DEVICES" -eq 0 ]; then
    echo -e "${RED}❌ No devices connected!${NC}"
    echo "   Please connect a device or start emulator"
    exit 1
fi

DEVICE_NAME=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
echo -e "${GREEN}✓${NC} Device found: ${DEVICE_NAME}"
echo ""

# Step 2: Clean old results and caches on device
echo -e "${YELLOW}[2/5]${NC} Cleaning old results and caches on device..."
adb shell "rm -rf $DEVICE_RESULTS_DIR/*" 2>/dev/null || true

# Clear ImageLoader caches
echo "   Clearing ImageLoader caches..."
adb shell "pm clear com.example.imageloader.test" 2>/dev/null || true

echo -e "${GREEN}✓${NC} Old results and caches cleaned"
echo ""

# Step 3: Build and install test APK
echo -e "${YELLOW}[3/5]${NC} Building and installing test APK..."
cd "$PROJECT_DIR"
./gradlew :ImageLoader:assembleDebugAndroidTest --quiet || {
    echo -e "${RED}❌ Build failed!${NC}"
    exit 1
}

./gradlew :ImageLoader:installDebugAndroidTest --quiet || {
    echo -e "${RED}❌ Install failed!${NC}"
    exit 1
}

echo -e "${GREEN}✓${NC} Test APK built and installed"
echo ""

# Step 4: Run MacroBenchmark test
echo -e "${YELLOW}[4/5]${NC} Running MacroBenchmark test..."
echo -e "${BLUE}   This will:${NC}"
echo -e "${BLUE}   - Launch RecyclerView with 500 items${NC}"
echo -e "${BLUE}   - Scroll down/up multiple times${NC}"
echo -e "${BLUE}   - Measure real-world performance${NC}"
echo -e "${BLUE}   Takes about 60-90 seconds...${NC}"
echo ""

# Clear logcat
adb logcat -c

# Run test
adb shell am instrument -w -e class \
  com.example.imageloader.benchmark.suite.MacroBenchmark#testRecyclerViewScrollBenchmark \
  com.example.imageloader.test/androidx.test.runner.AndroidJUnitRunner \
  2>&1 | grep -E "(Phase|Starting|Scrolling|Final|Generated|complete|✅|📊|🚀|🔥|📥|📤|🔄|⚡)" || true

TEST_EXIT_CODE=${PIPESTATUS[0]}

if [ $TEST_EXIT_CODE -ne 0 ]; then
    echo -e "${RED}❌ Test failed with exit code: $TEST_EXIT_CODE${NC}"
    echo "   Check logcat: adb logcat | grep MacroBenchmark"
    exit 1
fi

echo ""
echo -e "${GREEN}✓${NC} MacroBenchmark test completed!"
echo ""

# Step 5: Pull results from device
echo -e "${YELLOW}[5/5]${NC} Pulling results from device..."

# Create local results directory
mkdir -p "$LOCAL_RESULTS_DIR"

# Pull files
adb pull "$DEVICE_RESULTS_DIR/" "$LOCAL_RESULTS_DIR/" 2>&1 | grep -v "pulled" || true

# Fix nested directory if needed
if [ -d "$LOCAL_RESULTS_DIR/benchmark-results" ]; then
    mv "$LOCAL_RESULTS_DIR/benchmark-results"/* "$LOCAL_RESULTS_DIR/" 2>/dev/null || true
    rmdir "$LOCAL_RESULTS_DIR/benchmark-results" 2>/dev/null || true
fi

# Count files
JSON_COUNT=$(find "$LOCAL_RESULTS_DIR" -maxdepth 1 -name "*.json" 2>/dev/null | wc -l | tr -d ' ')
CSV_COUNT=$(find "$LOCAL_RESULTS_DIR" -maxdepth 1 -name "*.csv" 2>/dev/null | wc -l | tr -d ' ')
HTML_COUNT=$(find "$LOCAL_RESULTS_DIR" -maxdepth 1 -name "*.html" 2>/dev/null | wc -l | tr -d ' ')

echo -e "${GREEN}✓${NC} Results pulled:"
echo "   JSON files: $JSON_COUNT"
echo "   CSV files:  $CSV_COUNT"
echo "   HTML files: $HTML_COUNT"
echo ""

# Show summary from latest JSON
LATEST_JSON=$(find "$LOCAL_RESULTS_DIR" -name "benchmark-*.json" -type f 2>/dev/null | sort | tail -1)

if [ -n "$LATEST_JSON" ]; then
    echo -e "${BLUE}╔════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║   MacroBenchmark Summary                           ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    # Extract key metrics
    TOTAL_REQUESTS=$(grep -o '"totalRequests":[0-9]*' "$LATEST_JSON" | head -1 | cut -d':' -f2)
    CACHE_EFFICIENCY=$(grep -o '"cacheEfficiency":[0-9.]*' "$LATEST_JSON" | head -1 | cut -d':' -f2)
    OVERALL_SCORE=$(grep -o '"overallScore":[0-9.]*' "$LATEST_JSON" | head -1 | cut -d':' -f2)
    AVG_FPS=$(grep -o '"avgFPS":[0-9.]*' "$LATEST_JSON" | head -1 | cut -d':' -f2)
    
    if [ -n "$TOTAL_REQUESTS" ] && [ "$TOTAL_REQUESTS" != "0" ]; then
        echo -e "   ${GREEN}✓${NC} Total Requests: ${TOTAL_REQUESTS}"
        echo -e "   ${GREEN}✓${NC} Cache Efficiency: ${CACHE_EFFICIENCY}%"
        echo -e "   ${GREEN}✓${NC} Overall Score: ${OVERALL_SCORE}/100"
        [ -n "$AVG_FPS" ] && echo -e "   ${GREEN}✓${NC} Avg FPS: ${AVG_FPS}"
    else
        echo -e "   ${RED}⚠${NC}  Warning: Metrics are zero"
    fi
    echo ""
fi

echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✨ MacroBenchmark complete!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo ""
echo "📂 Results location: $LOCAL_RESULTS_DIR"
echo ""
echo "📊 View results:"
echo "   cat $LATEST_JSON | python3 -m json.tool"
echo "   open $LOCAL_RESULTS_DIR/benchmark-*.html"
echo ""
echo "🔍 View detailed logs:"
echo "   adb logcat -d | grep MacroBenchmark"
echo ""
