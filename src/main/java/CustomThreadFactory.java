import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final int keepAliveTime;
    private final TimeUnit timeUnit;
    private final CustomThreadPool pool;

    public CustomThreadFactory(int keepAliveTime, TimeUnit timeUnit, CustomThreadPool pool) {
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.pool = pool;
    }

    public Worker newWorker() {
        String threadName = "CustomThreadPool-Worker-" + threadNumber.getAndIncrement();
        Worker t = new Worker(threadName, keepAliveTime, timeUnit, pool);
        t.setDaemon(false);
        t.start();
        return t;
    }
}