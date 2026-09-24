const val BUCKET_SPAN = 4L
const val N_BUCKETS = 256

class SimpleData(
    val buckets: LongArray = LongArray(N_BUCKETS),
    var sum: Long = 0,
    var count: Long = 0,
    var min: Long = Long.MAX_VALUE,
    var max: Long = Long.MIN_VALUE,
) {
    fun percentile(p: Double): Long {
        val target = p * sum

        var cumSum = 0L
        for (i in buckets.indices) {
            cumSum += buckets[i]
            if (cumSum >= target) return i * BUCKET_SPAN
        }

        return BUCKET_SPAN * buckets.size
    }
}
