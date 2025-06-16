import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class.getName());
    private static final List<Long> allScenarioDurations = new ArrayList<>();

    public static void main(String[] args) {
        CustomThreadPool customPool = new CustomThreadPool(
                16, 32, 3, TimeUnit.SECONDS, 100, 1
        );

        logger.info("====== Start app ======");

        runAllScenarios(customPool, "CustomThreadPool");

        logger.info("====== Initiating shutdown ======");
        customPool.shutdown();
        try {
            if (!customPool.awaitTermination(20, TimeUnit.SECONDS)) {
                logger.warn("Pool did not terminate in time, forcing shutdown...");
                customPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Termination interrupted");
            customPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        ThreadPoolExecutor fixedThreadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(16);
        runAllScenarios(fixedThreadPool, "FixedThreadPool");
        fixedThreadPool.shutdown();

        logger.info("====== End app ======");
    }

    static void runAllScenarios(Executor executor, String labelPrefix) {
        runScenario(executor, labelPrefix + " - 1. Умеренная нагрузка", 10, 10);
        runScenario(executor, labelPrefix + " - 2. Всплеск задач", 2000, 0);
        runScenario(executor, labelPrefix + " - 3. Высокая нагрузка", 800, 200);
        runScenario(executor, labelPrefix + " - 4. Долгие задачи", 6, 5000);
        runMixedScenario(executor, labelPrefix + " - 5. Смешанные задачи");
    }

    static void runScenario(Executor executor, String label, int taskCount, int sleepTimeMs) {
        logger.info("\n === " + label + " ===");
        List<Long> durations = Collections.synchronizedList(new ArrayList<>());
        long totalTimeMs = sendTasks(executor, taskCount, sleepTimeMs, durations);
        allScenarioDurations.add(totalTimeMs);
        printStats(label, durations, taskCount - durations.size(), totalTimeMs);
    }

    static void runMixedScenario(Executor executor, String label) {
        logger.info("\n === " + label + " ===");
        int total = 10;
        CountDownLatch latch = new CountDownLatch(total);
        int rejected = 0;
        List<Long> durations = Collections.synchronizedList(new ArrayList<>());

        long start = System.nanoTime();

        for (int i = 0; i < total; i++) {
            int finali = i;
            int sleepTime = (i % 2 == 0) ? 500 : 4000;
            try {
                executor.execute(() -> {
                    long t0 = System.nanoTime();
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    long duration = System.nanoTime() - t0;
                    durations.add(TimeUnit.NANOSECONDS.toMillis(duration));
                    logger.info("Task " + finali + " finished (" + sleepTime + "ms)");
                    latch.countDown();
                });
            } catch (Exception e) {
                logger.warn("Task " + finali + " rejected");
                latch.countDown();
                rejected++;
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long totalTime = System.nanoTime() - start;
        long totalTimeMs = TimeUnit.NANOSECONDS.toMillis(totalTime);
        allScenarioDurations.add(totalTimeMs);

        printStats(label, durations, rejected, totalTimeMs);
    }

    static long sendTasks(Executor executor, int count, int sleepTimeMs, List<Long> durations) {
        CountDownLatch latch = new CountDownLatch(count);
        long startTime = System.nanoTime();

        for (int i = 0; i < count; i++) {
            int taskId = i;
            try {
                executor.execute(() -> {
                    long t0 = System.nanoTime();
                    logger.info("Task {} started", taskId);
                    try {
                        Thread.sleep(sleepTimeMs);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    long duration = System.nanoTime() - t0;
                    durations.add(duration);
                    logger.info("Task {} finished", taskId);
                    latch.countDown();
                });
            } catch (Exception e) {
                logger.warn("Task {} rejected", taskId);
                latch.countDown();
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.nanoTime();
        return TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
    }

    static void printStats(String label, List<Long> durations, int rejected, long totalTimeMs) {
        long total = durations.stream().mapToLong(TimeUnit.NANOSECONDS::toMillis).sum();
        long count = durations.size();
        double avg = count > 0 ? (double) total / count : 0;

        logger.info("\n=== Stats for " + label + " ===");
        logger.info("Completed tasks: " + count);
        logger.info("Rejected tasks: " + rejected);
        logger.info("Total scenario time (ms): " + totalTimeMs);
        logger.info("Average task duration (ms): " + String.format("%.2f", avg));
    }
}

