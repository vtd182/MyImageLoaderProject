package com.example.imageloader.benchmark

/**
 * TestDataGenerator - Generate test image URLs for benchmarking.
 *
 * ## Features:
 * - Multiple size categories (tiny → huge)
 * - Format variations (jpg, webp)
 * - Controlled dataset for reproducible benchmarks
 * - Mix of Lorem Picsum (unlimited) + Unsplash URLs
 *
 * ## Usage:
 * ```kotlin
 * val urls = TestDataGenerator.generateBenchmarkUrls(count = 1000)
 * val mixedSizes = TestDataGenerator.mixedSizeUrls(100)
 * ```
 */
object TestDataGenerator {
    
    /**
     * Pre-defined Unsplash photo IDs for consistent testing.
     * These are real, high-quality photos from Unsplash.
     */
    private val unsplashPhotoIds = listOf(
        "tMI2_-r5Nfo", "ZVw3HCW_C_g", "xII7efH1G6o", "2JIvboGLeho",
        "3y1zF4hIPCg", "RL0eD9B8z7Y", "IicyiaPYGGI", "unsplash-image-id-5",
        "tGTVxeOr_Rs", "Bkci_8qcdvQ", "lFmuWU0tv4M", "D_rMfhC7qD8",
        "Dm-qxdynoEc", "qDG7XKJLKbs", "3MAmj1ZKSZA", "ufJzB_1nVqs"
    )
    
    data class ImageSpec(
        val url: String,
        val category: SizeCategory,
        val format: ImageFormat,
        val expectedSizeKB: Int
    )
    
    enum class SizeCategory(val width: Int, val height: Int) {
        TINY(200, 200),
        SMALL(400, 600),
        MEDIUM(1080, 1440),
        LARGE(2560, 1440),
        HUGE(4096, 4096)
    }
    
    enum class ImageFormat {
        JPEG, WEBP
    }
    
    /**
     * Generate Lorem Picsum URLs with specified size and count.
     *
     * Lorem Picsum offers:
     * - Unlimited requests (no rate limit)
     * - Consistent images (same ID = same image)
     * - Size control via URL
     *
     * @param count Number of URLs to generate
     * @param category Size category
     * @return List of image specs
     */
    fun generatePicsumUrls(count: Int, category: SizeCategory): List<ImageSpec> {
        return (1..count).map { id ->
            ImageSpec(
                url = "https://picsum.photos/id/${id}/${category.width}/${category.height}.jpg",
                category = category,
                format = ImageFormat.JPEG,
                expectedSizeKB = estimateSizeKB(category)
            )
        }
    }
    
    /**
     * Generate Unsplash URLs (use sparingly due to rate limits).
     *
     * @param count Number of URLs (max 50 due to rate limits)
     * @return List of high-quality Unsplash image specs
     */
    fun generateUnsplashUrls(count: Int = 16): List<ImageSpec> {
        require(count <= unsplashPhotoIds.size) {
            "Only ${unsplashPhotoIds.size} Unsplash IDs available"
        }
        
        return unsplashPhotoIds.take(count).map { id ->
            ImageSpec(
                url = "https://images.unsplash.com/photo-${id}?w=1080&q=80",
                category = SizeCategory.MEDIUM,
                format = ImageFormat.JPEG,
                expectedSizeKB = 300
            )
        }
    }
    
    /**
     * Generate mixed dataset mimicking real-world usage.
     *
     * Distribution:
     * - 60% small (typical list items)
     * - 20% medium (full screen)
     * - 15% tiny (thumbnails)
     * - 5% large (high-res)
     *
     * @param totalCount Total number of images
     * @return Realistic mix of image specs
     */
    fun generateMixedDataset(totalCount: Int): List<ImageSpec> {
        val distribution = mapOf(
            SizeCategory.TINY to 0.15,
            SizeCategory.SMALL to 0.60,
            SizeCategory.MEDIUM to 0.20,
            SizeCategory.LARGE to 0.05
        )
        
        return distribution.flatMap { (category, ratio) ->
            val count = (totalCount * ratio).toInt()
            generatePicsumUrls(count, category)
        }
    }
    
    /**
     * Generate stress test dataset (large images).
     *
     * @param count Number of large/huge images
     * @return List of memory-intensive image specs
     */
    fun generateStressTestDataset(count: Int): List<ImageSpec> {
        val largeCount = (count * 0.7).toInt()
        val hugeCount = count - largeCount
        
        return generatePicsumUrls(largeCount, SizeCategory.LARGE) +
                generatePicsumUrls(hugeCount, SizeCategory.HUGE)
    }
    
    /**
     * Generate format comparison dataset (same image, different formats).
     *
     * @return Pairs of JPEG/WebP URLs for comparison
     */
    fun generateFormatComparisonUrls(): List<Pair<ImageSpec, ImageSpec>> {
        return (1..50).map { id ->
            val jpeg = ImageSpec(
                url = "https://picsum.photos/id/$id/1080/1440.jpg",
                category = SizeCategory.MEDIUM,
                format = ImageFormat.JPEG,
                expectedSizeKB = 300
            )
            val webp = ImageSpec(
                url = "https://picsum.photos/id/$id/1080/1440.webp",
                category = SizeCategory.MEDIUM,
                format = ImageFormat.WEBP,
                expectedSizeKB = 200
            )
            jpeg to webp
        }
    }
    
    /**
     * Generate benchmark suite with all categories.
     *
     * @return Comprehensive test dataset
     */
    fun generateComprehensiveBenchmarkSuite(): BenchmarkSuite {
        return BenchmarkSuite(
            tiny = generatePicsumUrls(100, SizeCategory.TINY),
            small = generatePicsumUrls(500, SizeCategory.SMALL),
            medium = generatePicsumUrls(200, SizeCategory.MEDIUM),
            large = generatePicsumUrls(50, SizeCategory.LARGE),
            huge = generatePicsumUrls(20, SizeCategory.HUGE),
            unsplash = generateUnsplashUrls(16),
            mixed = generateMixedDataset(1000)
        )
    }
    
    data class BenchmarkSuite(
        val tiny: List<ImageSpec>,
        val small: List<ImageSpec>,
        val medium: List<ImageSpec>,
        val large: List<ImageSpec>,
        val huge: List<ImageSpec>,
        val unsplash: List<ImageSpec>,
        val mixed: List<ImageSpec>
    ) {
        val totalCount: Int = tiny.size + small.size + medium.size + 
                              large.size + huge.size + unsplash.size + mixed.size
    }
    
    private fun estimateSizeKB(category: SizeCategory): Int {
        val pixels = category.width * category.height
        // Rough estimate: JPEG compression ~1 byte per 10 pixels
        return (pixels / 10 / 1024)
    }
}
