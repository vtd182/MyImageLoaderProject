#!/bin/bash

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   ImageLoader Realistic MacroBenchmark           ║${NC}"
echo -e "${BLUE}║   (Natural User Behavior Simulation)              ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════╝${NC}"
echo ""

# Check for connected devices
echo -e "${YELLOW}[1/5]${NC} Checking for connected devices..."
DEVICE=$(adb devices | grep -w "device" | head -1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo -e "${RED}✗${NC} No device found. Please connect a device or start an emulator."
    exit 1
fi
echo -e "${GREEN}✓${NC} Device found: $DEVICE"
echo ""

# Clean old results
echo -e "${YELLOW}[2/5]${NC} Cleaning old results and caches..."
echo "   Clearing ImageLoader caches..."
adb shell pm clear com.example.imageloader.test 2>/dev/null
echo -e "${GREEN}✓${NC} Caches cleaned"
echo ""

# Build and install
echo -e "${YELLOW}[3/5]${NC} Building and installing test APK..."
./gradlew :ImageLoader:assembleDebugAndroidTest :ImageLoader:installDebugAndroidTest --quiet
if [ $? -ne 0 ]; then
    echo -e "${RED}✗${NC} Build failed"
    exit 1
fi
echo -e "${GREEN}✓${NC} Test APK built and installed"
echo ""

# Run test
echo -e "${YELLOW}[4/5]${NC} Running RealisticMacroBenchmark test..."
echo -e "${BLUE}   This will:${NC}"
echo -e "${BLUE}   - Scroll half-screen at a time (natural)${NC}"
echo -e "${BLUE}   - Wait for images based on size (large/huge = longer)${NC}"
echo -e "${BLUE}   - Load 200 unique images in Phase 1${NC}"
echo -e "${BLUE}   - Scroll back up in Phase 2 (test cache)${NC}"
echo -e "${BLUE}   Takes about 3-5 minutes...${NC}"
echo ""

adb shell am instrument -w -r \
    -e debug false \
    -e class 'com.example.imageloader.benchmark.suite.RealisticMacroBenchmark#testRealisticScrollBehavior' \
    com.example.imageloader.test/androidx.test.runner.AndroidJUnitRunner

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓${NC} RealisticMacroBenchmark test completed!"
else
    echo ""
    echo -e "${RED}✗${NC} Test failed or was interrupted"
    exit 1
fi
echo ""

# Pull results
echo -e "${YELLOW}[5/5]${NC} Pulling results from device..."
RESULTS_DIR="./benchmark-results"
mkdir -p "$RESULTS_DIR"

# Pull all benchmark files
adb pull /sdcard/Android/data/com.example.imageloader.test/files/benchmark-results/ "$RESULTS_DIR/" 2>/dev/null

# Count files
JSON_COUNT=$(find "$RESULTS_DIR" -name "realistic-benchmark-*.json" -type f 2>/dev/null | wc -l | tr -d ' ')
HTML_COUNT=$(find "$RESULTS_DIR" -name "realistic-benchmark-*.html" -type f 2>/dev/null | wc -l | tr -d ' ')

echo -e "${GREEN}✓${NC} Results pulled:"
echo "   JSON files: $JSON_COUNT"
echo "   HTML files: $HTML_COUNT"
echo ""

# Show summary
echo -e "${BLUE}╔════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Realistic Benchmark Summary                     ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════╝${NC}"
echo ""

# Extract stats from logcat
echo -e "   Extracting test stats from logs..."
adb logcat -d | grep "FINAL RESULTS" -A 10 | tail -11

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✨ RealisticMacroBenchmark complete!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo ""

echo "📂 Results location: $RESULTS_DIR"
echo ""
echo "📊 View results:"
LATEST_HTML=$(find "$RESULTS_DIR" -name "realistic-benchmark-*.html" -type f 2>/dev/null | sort -r | head -1)
if [ -n "$LATEST_HTML" ]; then
    echo "   open $LATEST_HTML"
fi
echo ""
echo "🔍 View detailed logs:"
echo "   adb logcat -d | grep RealisticMacroBenchmark"
echo ""
