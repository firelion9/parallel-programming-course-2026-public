package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    private Thread owner = null;

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) {
        this.name = name;
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        Scheduler.await(() -> owner == null);
        owner = Thread.currentThread();
        Scheduler.acquireLock(name);
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        if (owner != Thread.currentThread()) {
            throw new IllegalArgumentException("Trying to unlock lock held by another thread");
        }
        owner = null;
        Scheduler.releaseLock(name);
        Scheduler.interruptPoint();
    }
}
