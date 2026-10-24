package lab2;

import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int  pick(List<Integer> enabled, int last) {
            return enabled.get(0);
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private final StringCharacterIterator itr;
        public Replay(String trace) {
            this.trace = trace;
            itr = new StringCharacterIterator(trace);
        }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() { }

        public int pick(List<Integer> enabled, int last) {
            char x = itr.current();
            itr.next();
            return x - 'A';
        }

        @Override
        public String toString() {
            return trace;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final Random random;

        public RandomPick(long seed) {
            random = new Random(seed);
        }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(random.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private final int bound;
        private final List<StackEntry> stack = new ArrayList<>();
        private int currentDepth = 0;
        private int switches = 0;

        public Dfs(int bound) {
            this.bound = bound;
        }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            currentDepth = 0;
            switches = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            if (switches >= bound) {
                if (enabled.contains(last)) return last;
            }

            var nextAlternative = 0;
            if (currentDepth == stack.size()) {
                stack.add(new StackEntry(0, enabled.size() == 1));
            } else if (currentDepth == stack.size() - 1) {
                var prev = stack.remove(stack.size() - 1);
                nextAlternative = prev.alternative + 1;
                stack.add(new StackEntry(nextAlternative, nextAlternative == enabled.size() - 1));
            } else {
                nextAlternative = stack.get(currentDepth).alternative;
            }
            currentDepth += 1;

            var nextId = enabled.get(nextAlternative);
            switches += last >= 0 && nextId != last && (enabled.contains(last)) ? 1 : 0;

            return nextId;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            while (!stack.isEmpty()) {
                if (stack.get(stack.size() - 1).isLast) stack.remove(stack.size() - 1);
                else break;
            }

            return !stack.isEmpty();
        }

        private record StackEntry(int alternative, boolean isLast) {}
    }
}
