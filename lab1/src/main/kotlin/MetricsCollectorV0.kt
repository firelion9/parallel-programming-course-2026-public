import kotlin.math.max
import kotlin.math.min

class MetricsCollectorV0 : MetricsCollector {
    private val data = Data()

    override fun record(value: Long) {
        data.add(value)
    }

    override fun snapshot(): Snapshot {
        val data = data.snapshot()

        val p50 = data.percentile(0.5)
        val p99 = data.percentile(0.99)
        return Snapshot(
            data.buckets,
            data.count,
            data.sum,
            data.min,
            data.max,
            p50,
            p99,
        )
    }

    private class Data(
        val buckets: LongArray = LongArray(N_BUCKETS),
        var sum: Long = 0,
        var count: Long = 0,
        var min: Long = Long.MAX_VALUE,
        var max: Long = Long.MIN_VALUE,
    ) {

        fun add(value: Long) {
            val bucket = (value / BUCKET_SPAN).coerceIn(0, buckets.lastIndex.toLong()).toInt()
            buckets[bucket] += 1
            count += 1
            sum += value
            min = min(min, value)
            max = max(max, value)
        }

        fun snapshot(): SimpleData = SimpleData(
            buckets = buckets.copyOf(),
            sum = sum,
            count = count,
            min = min,
            max = max
        )
    }
}
