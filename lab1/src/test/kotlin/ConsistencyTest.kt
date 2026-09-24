@file:OptIn(ExperimentalAtomicApi::class)

import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.thread
import kotlin.random.Random

abstract class ConsistencyTest {
    abstract fun makeCollector(): MetricsCollector

    @Test
    fun `test snapshot consistency`() {
        val collector = makeCollector()

        val latch = CountDownLatch(1)
        val running = AtomicBoolean(true)
        val calls = AtomicLong(0)

        val threads = (0 until THREAD_COUNT).map { k ->
            thread {
                val rng = Random(k)
                latch.await()
                while (running.load()) {
                    collector.record(rng.nextLong(0, N_BUCKETS * BUCKET_SPAN))
                    calls.incrementAndGet()
                }
            }
        }

        latch.countDown()
        var failures = 0
        var countLess = 0
        repeat(N_SNAPSHOTS) {
            val snapshot = collector.snapshot()
            val sum = snapshot.buckets.sum()
            if (sum != snapshot.count) failures += 1
            if (snapshot.count < sum) countLess += 1
        }
        running.store(false)
        threads.forEach { it.join() }

        val diff = collector.snapshot().count - calls.get()

        println(diff)
        assert(failures == 0) {
            "$failures (${100.0 * failures / N_SNAPSHOTS}% | ${failures - countLess} | $countLess | $diff) broken snapshots were detected while using $collector"
        }
    }

    companion object {
        private const val THREAD_COUNT = 4
        private const val N_SNAPSHOTS = 10000
    }
}

class ConsistencyV0 : ConsistencyTest() {
    override fun makeCollector(): MetricsCollector = MetricsCollectorV0()
}

class ConsistencyV1 : ConsistencyTest() {
    override fun makeCollector(): MetricsCollector = MetricsCollectorV1()
}

class ConsistencyV1a : ConsistencyTest() {
    override fun makeCollector(): MetricsCollector = MetricsCollectorV1a()
}

class ConsistencyV2 : ConsistencyTest() {
    override fun makeCollector(): MetricsCollector = MetricsCollectorV2()
}

class ConsistencyV3 : ConsistencyTest() {
    override fun makeCollector(): MetricsCollector = MetricsCollectorV3()
}

class ConsistencyV4 : ConsistencyTest() {
    override fun makeCollector(): MetricsCollector = MetricsCollectorV4()
}

class ConsistencyV4a : ConsistencyTest() {
    override fun makeCollector(): MetricsCollector = MetricsCollectorV4a()
}
