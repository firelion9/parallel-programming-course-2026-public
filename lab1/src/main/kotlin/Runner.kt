@file:OptIn(ExperimentalAtomicApi::class)

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLongArray
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val DATA_SIZE = 1 shl 20
private val dist = run {
    val res = DoubleArray((N_BUCKETS * BUCKET_SPAN).toInt()) {
        if (it == 0) 0.0 else it.toDouble().pow(-1.15)
    }
    val sum = res.sum()
    for (i in 0 until res.lastIndex) {
        res[i + 1] += res[i]
        res[i] /= sum
    }

    res
}

private fun genSample(rng: Random): Long {
    val uniform = rng.nextDouble()
    val idx = dist.binarySearch(uniform)
    return idx.absoluteValue.toLong()
}

private fun genData(seed: Long): LongArray {
    val rng = Random(seed)

    return LongArray(DATA_SIZE) { genSample(rng) }
}

private fun run(collector: MetricsCollector, threadCount: Int, data: LongArray, duration: Duration): Double {
    val latch = CountDownLatch(1)
    val running = AtomicBoolean(true)
    val ops = AtomicLongArray(threadCount)

    val threads = (0 until threadCount).map { k ->
        thread {
            var myOps = 0L
            var idx = k * 1000
            latch.await()
            while (running.load()) {
                collector.record(data[idx++])
                myOps += 1
                if (idx >= data.size) idx = 0
            }
            ops.storeAt(k, myOps)
        }
    }

    val start = System.nanoTime()
    latch.countDown()
    Thread.sleep(duration.inWholeMilliseconds)
    running.store(false)
    val end = System.nanoTime()
    threads.forEach { it.join() }

    return (0 until ops.size).sumOf { ops.loadAt(it) } / ((end - start) * 1.0)
}

fun measure(collector: MetricsCollector, threadCount: Int, seed: Long): Double {
    val data = genData(seed)
    run(collector, threadCount, data, 5.seconds)
    val results = (0 until 5).mapTo(mutableListOf()) { run(collector, threadCount, data, 5.seconds) }
    System.err.println(collector.snapshot().count)
    results.sort()
    return results[2]
}

private val collectors: List<Pair<() -> MetricsCollector, String>> = listOf(
    ::MetricsCollectorV0 to "V0",
    ::MetricsCollectorV1 to "V1",
    ::MetricsCollectorV2 to "V2",
    ::MetricsCollectorV3 to "V3",
    ::MetricsCollectorV4 to "V4",
    ::MetricsCollectorV1a to "V1a",
)

private val experiments = """
0 1 42
1 1 42
1 2 42
1 4 42
1 8 42
1 16 42
2 1 42
2 2 42
2 4 42
2 8 42
2 16 42
3 1 42
3 2 42
3 4 42
3 8 42
3 16 42
4 1 42
4 2 42
4 4 42
4 8 42
4 16 42
5 1 42
5 2 42
5 4 42
5 8 42
5 16 42
""".trimIndent().split("\n").map { line ->
    val (idx, threads, seed) = line.split(" ")
    Triple(idx.toInt(), threads.toInt(), seed.toLong())
}

fun runExperiment(collectorIdx: Int, nThreads: Int, seed: Long) {
    val collector = collectors[collectorIdx].first()
    val collectorName = collectors[collectorIdx].second
    println("$collectorName,$nThreads,${measure(collector, nThreads, seed)}")

}

fun main(args: Array<String>) {
    val errStream = ByteArrayOutputStream()
    val err = PrintStream(errStream)
    val prevErr = System.err
    System.setErr(err)

    if (args.size == 3) {
        runExperiment(args[0].toInt(), args[1].toInt(), args[2].toLong())
    } else {
        println("Ver,thr,Gops/s")
        for ((collectorIdx, nThreads, seed) in experiments) {
            runExperiment(collectorIdx, nThreads, seed)
        }
    }

    System.setErr(prevErr)
    err.close()
    System.err.println(errStream.toString())
}
