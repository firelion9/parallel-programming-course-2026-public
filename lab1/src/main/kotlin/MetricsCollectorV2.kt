@file:OptIn(ExperimentalAtomicApi::class)

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.asJavaAtomic
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.math.max
import kotlin.math.min

class MetricsCollectorV2 : MetricsCollector {
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
        var sum: AtomicLong = AtomicLong(0),
        var count: AtomicLong = AtomicLong(0),
        var min: AtomicLong = AtomicLong(Long.MAX_VALUE),
        var max:AtomicLong = AtomicLong(Long.MIN_VALUE),
        val locks: Array<Any> = Array(N_LOCKS) { Any() }
    ) {

        fun add(value: Long) {
            val bucket = (value / BUCKET_SPAN).coerceIn(0, buckets.lastIndex.toLong()).toInt()
            val lockIdx = bucket / BUCKETS_PER_LOCK
            synchronized(locks[lockIdx]) {
                buckets[bucket] += 1
            }
            count.fetchAndIncrement()
            sum.fetchAndAdd(value)
            min.asJavaAtomic().accumulateAndGet(value, ::min)
            max.asJavaAtomic().accumulateAndGet(value, ::max)
        }

        fun snapshot(): SimpleData {
            val bucketsCopy = LongArray(N_BUCKETS)
            for (lockIdx in 0 until N_LOCKS) {
                val fromIdx = lockIdx * BUCKETS_PER_LOCK
                val toIdx = fromIdx + BUCKETS_PER_LOCK
                buckets.copyInto(bucketsCopy, fromIdx, fromIdx, toIdx)
            }

            return SimpleData(
                buckets = bucketsCopy,
                sum = sum.load(),
                count = count.load(),
                min = min.load(),
                max = max.load(),
            )
        }
    }

    companion object {
        private const val N_LOCKS: Int = 16
        private const val BUCKETS_PER_LOCK: Int = N_BUCKETS / N_LOCKS
    }
}
