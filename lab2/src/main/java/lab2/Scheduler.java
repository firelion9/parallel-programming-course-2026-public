package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    private static Scheduler currentInstance;

    private final List<ThreadState> threads = new ArrayList<>();
    private final ThreadLocal<ThreadState> localState = new ThreadLocal<>();
    private final Semaphore taskSemaphore = new Semaphore(0);

    private final List<String> heldLocks = new ArrayList<>();

    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    final StringBuilder trace = new StringBuilder();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        var state = new ThreadState(threads.size());

        var thread = new Thread(() -> {
            localState.set(state);
            state.isRunning = true;
            state.isAlive = true;
            try {
                body.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
            state.isAlive = false;
            taskSemaphore.release();
        });
        state.thread = thread;
        threads.add(state);
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        if (currentInstance != null) throw new IllegalStateException("Nested run");
        currentInstance = this;
        logging = true;
        int currentThread = -1;

        try {
            for (ThreadState state : threads) {
                state.thread.start();
                try {
                    taskSemaphore.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            do {
                var toRun = threads.stream().filter((s) -> s.isAlive && (s.waitCondition == null || s.waitCondition.getAsBoolean())).map((s) -> s.index).toList();
                if (toRun.isEmpty()) break;

                currentThread = strategy.pick(toRun, currentThread);
                trace.append(Character.toString('A' + currentThread));
                threads.get(currentThread).semaphore.release();
                try {
                    taskSemaphore.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } while (true);
            if (!heldLocks.isEmpty()) {
                deadlock = "held=" + heldLocks;
            }
        } finally {
            for (var s : threads) {
                if (s.isAlive) s.thread.interrupt();
            }
            try {
                for (var s : threads) {
                    if (s.thread.isAlive()) s.thread.join();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            logging = false;
            currentInstance = null;
        }
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return String.join("", trace);
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        logOp("window");
    }

    public static void interruptPoint() {
        var scheduler = currentInstance;
        var state = scheduler.localState.get();
        state.isRunning = false;
        scheduler.taskSemaphore.release();
        try {
            state.semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        state.isRunning = true;
    }

    public static void logOp(String val) {
        var scheduler = currentInstance;
        if (!scheduler.logging) return;
        scheduler.log.add(scheduler.localState.get().threadLabel() + "   " + val);
    }

    public static void acquireLock(String name) {
        var scheduler = currentInstance;
        var threadName = scheduler.localState.get().threadLabel();
        scheduler.heldLocks.add(threadName + ":" + name);
        logOp("acquire " + name);
    }

    public static void releaseLock(String name) {
        var scheduler = currentInstance;
        var threadName = scheduler.localState.get().threadLabel();
        scheduler.heldLocks.remove(threadName + ":" + name);
        logOp("release " + name);
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        currentInstance.localState.get().waitCondition = cond;
        interruptPoint();
    }

    private static class ThreadState {
        public Thread thread;
        public final int index;
        public final Semaphore semaphore = new Semaphore(0);
        public boolean isRunning = false;
        public BooleanSupplier waitCondition = null;

        public boolean isAlive = false;

        public String threadLabel() {
            return Character.toString(index + 'A');
        }

        private ThreadState(int index) {
            this.index = index;
        }
    }
}
