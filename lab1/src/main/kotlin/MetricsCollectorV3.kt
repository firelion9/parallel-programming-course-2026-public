import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.max
import kotlin.math.min

class MetricsCollectorV3 : MetricsCollector {
    private val allData: MutableList<Data> = mutableListOf()
    private val allDataLock: Any = Any()

    private val data = ThreadLocal<Data>.withInitial {
        val res = Data()
        synchronized(allDataLock) {
            allData.add(res)
        }
        res
    }

    override fun record(value: Long) {
        data.get().add(value)
    }

    override fun snapshot(): Snapshot {
        val allData = synchronized(allDataLock) {
            allData.toList()
        }
        val data = SimpleData()

        for (s in allData) {
            for (i in 0 until N_BUCKETS) data.buckets[i] += s.buckets.get(i)

            data.count += s.count.get()

            data.sum += s.sum.get()
            data.min = min(data.min, s.min.get())
            data.max = max(data.max, s.max.get())
        }

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
        val buckets: AtomicLongArray = AtomicLongArray(N_BUCKETS),
        var sum: AtomicLong = AtomicLong(0),
        var count: AtomicLong = AtomicLong(0),
        var min: AtomicLong = AtomicLong(Long.MAX_VALUE),
        var max: AtomicLong = AtomicLong(Long.MIN_VALUE),
    ) {

        fun add(value: Long) {
            val bucket = (value / BUCKET_SPAN).coerceIn(0, N_BUCKETS - 1L).toInt()
            buckets.setRelease(bucket, buckets.getPlain(bucket) + 1)
            count.setRelease(count.getPlain() + 1)
            sum.setRelease(sum.getPlain() + value)
            if (value < min.getPlain()) min.setRelease(value)
            if (value > max.getPlain()) max.setRelease(value)
        }
    }
}
