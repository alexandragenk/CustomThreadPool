import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CustomThreadPool implements CustomExecutor {
    private static final Logger logger = LogManager.getLogger(CustomThreadPool.class.getName());
    int corePoolSize;
    int maxPoolSize;
    int queueSize;
    int minSpareThreads;
    private volatile boolean isShutdown = false;
    private final Set<Worker> workers = new HashSet<>();
    private final CustomThreadFactory threadFactory;

    public CustomThreadPool(int corePoolSize, int maxPoolSize, int keepAliveTime, TimeUnit timeUnit, int queueSize, int minSpareThreads) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.minSpareThreads = minSpareThreads;
        this.queueSize = queueSize;
        this.threadFactory = new CustomThreadFactory(keepAliveTime, timeUnit, this);
        for (int i = 0; i <= corePoolSize + minSpareThreads; i++) {
            workers.add(threadFactory.newWorker());
        }
    }

    @Override
    public synchronized void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("Command is null");
        }
        if (isShutdown) {
            logger.info("");
            throw new RejectedException("CustomThreadPool is shutting down");
        }
        Worker minQueueSize = null;

        for (Worker worker : workers) {
            if (minQueueSize == null || minQueueSize.getSize() > worker.getSize()) {
                minQueueSize = worker;
            }
        }
        if (minQueueSize.getSize() != 0 && workers.size() < maxPoolSize){
            minQueueSize = threadFactory.newWorker();
            workers.add(minQueueSize);
            logger.info("New Worker added to pool");
        }
        if (!minQueueSize.addCommand(command)){
            logger.info("Task was rejected due to overload!");
            throw new RejectedException("Pool is overflow");
        };
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        execute(() -> {
            try {
                future.complete(callable.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
    }

    @Override
    public synchronized void shutdownNow() {
        isShutdown = true;
        for (Worker worker : workers) {
            worker.interrupt();
        }
    }

    public synchronized boolean tryRemoveWorker(Worker worker){
        boolean isMinCount = isShutdown || workers.size() > corePoolSize + minSpareThreads;
        if (isMinCount) {
            logger.info("Try remove worker from pool");
            workers.remove(worker);
        }
        return isMinCount;

    }
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (!workers.isEmpty()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            wait(remaining);
        }
        return true;
    }

    public synchronized int getWorkerCount() {
        return workers.size();
    }
    public int getQueueSize() {
        return queueSize;
    }
}
