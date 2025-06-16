import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Worker extends Thread {
    private static final Logger logger = LogManager.getLogger(Worker.class.getName());
    private final LinkedBlockingQueue<Runnable> taskQueue;
    private final int keepAliveTime;
    private final TimeUnit timeUnit;
    private final CustomThreadPool customPool;

    public Worker(String name, int keepAliveTime, TimeUnit timeUnit, CustomThreadPool customPool) {
        super(name);
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.customPool = customPool;
        this.taskQueue = new LinkedBlockingQueue<>(customPool.getQueueSize());
    }

    public boolean addCommand(Runnable command) {
        return taskQueue.offer(command);
    }

    public int getSize() {
        return taskQueue.size();
    }

    @Override
    public void run() {
        logger.info("Creating new thread: " + getName());
        try {
            while (true) {
                Runnable task = taskQueue.poll(keepAliveTime, timeUnit);
                if (task != null) {
                    task.run();
                } else {
                    if (customPool.tryRemoveWorker(this)) {
                        logger.info("Idle timeout, stopping");
                        break;
                    }
                }
            }
        } catch (InterruptedException ignored) {
        } finally {
            boolean removed = customPool.tryRemoveWorker(this);
            if (removed) {
                synchronized (customPool) {
                    if (customPool.getWorkerCount() == 0) {
                        customPool.notifyAll();
                    }
                }
            }
            logger.info("Worker removed from pool");
        }
    }
}
