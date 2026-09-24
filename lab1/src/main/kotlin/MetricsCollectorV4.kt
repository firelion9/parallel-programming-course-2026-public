import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

class MetricsCollectorV4 : MetricsCollector {
    private val globalEpoch: AtomicInteger = AtomicInteger(0)
    private val allData: MutableList<Data> = mutableListOf()
    private val allDataLock: Any = Any()

    private val globalSnapshot: SimpleData = SimpleData()

    private val data = ThreadLocal<Data>.withInitial {
        val res = Data(globalEpoch)
        synchronized(allDataLock) {
            allData.add(res)
        }
        res
    }

    override fun record(value: Long) {
        data.get().add(value)
    }

    override fun snapshot(): Snapshot {
        synchronized(allDataLock) {
            val old = globalEpoch.get()
            globalEpoch.set(1 - old)
            for (s in allData) {
                while (s.insideIdx.get() == old) {
                    Thread.onSpinWait()
                }
            }

            val globalSnapshot = globalSnapshot
            for (st in allData) {
                val s = st.buffers[old]
                for (i in 0 until N_BUCKETS) {
                    globalSnapshot.buckets[i] += s.buckets[i]
                    s.buckets[i] = 0
                }

                globalSnapshot.count += s.count
                globalSnapshot.sum += s.sum
                globalSnapshot.min = min(globalSnapshot.min, s.min)
                globalSnapshot.max = max(globalSnapshot.max, s.max)

                s.count = 0
                s.sum = 0
                s.min = Long.MAX_VALUE
                s.max = Long.MIN_VALUE
            }

            val p50 = globalSnapshot.percentile(0.5)
            val p99 = globalSnapshot.percentile(0.99)
            return Snapshot(
                globalSnapshot.buckets.copyOf(),
                globalSnapshot.count,
                globalSnapshot.sum,
                globalSnapshot.min,
                globalSnapshot.max,
                p50,
                p99,
            )
        }
    }

    private class Data(
        val globalEpoch: AtomicInteger,
        val buffers: Array<SimpleData> = Array(2) { SimpleData() },
        val insideIdx: AtomicInteger = AtomicInteger(-1),
    ) {
        fun add(value: Long) {
            var epoch: Int
            while (true) {
                epoch = globalEpoch.get()
                insideIdx.set(epoch)
                if (globalEpoch.get() == epoch) break
                insideIdx.setRelease(-1)
            }
            with(buffers[epoch]) {
                val bucket = (value / BUCKET_SPAN).coerceIn(0, N_BUCKETS - 1L).toInt()
                buckets[bucket] += 1
                count += 1
                sum += value
                min = min(min, value)
                max = max(max, value)
            }
            insideIdx.setRelease(-1)
        }
    }
}
