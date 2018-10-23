package bdv.fx.viewer;

import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PriorityExecutorService {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static double DEFAULT_PRIORITY = 0.0;

	interface TaskWithPriority extends Comparable<TaskWithPriority>, Runnable
	{
		double priority();

		default boolean isValid()
		{
			return true;
		}

		default void cancel() {

		}

		@Override
		default int compareTo(@NotNull final TaskWithPriority that)
		{
			return Double.compare(that.priority(), this.priority());
		}
	}

	public static class WrapAsTask implements TaskWithPriority
	{

		private final double priority;

		private final Runnable r;

		private boolean isValid;

		public WrapAsTask(
				final Runnable r,
				final double priority) {
			this.r = r;
			this.priority = priority;
			this.isValid = true;
		}

		public void invalidate() {
			this.isValid = false;
		}

		@Override
		public void run() {
			r.run();
		}

		@Override
		public double priority() {
			return this.priority;
		}

		@Override
		public boolean isValid() {
			return this.isValid;
		}
	}

	private final PriorityBlockingQueue<TaskWithPriority> queue = new PriorityBlockingQueue<>();

	private final ExecutorService es;

	public PriorityExecutorService(final ExecutorService es)
	{
		this.es = es;
		Thread managerThread = new Thread(() -> {
			while (!es.isShutdown() && !es.isTerminated()) {
				TaskWithPriority nextTask = null;
				try {
					synchronized (this.queue) {
						nextTask = queue.take();
					}
				} catch (InterruptedException ignored) {

				}
				if (nextTask != null) {
					if (nextTask.isValid())
						es.submit(nextTask);
					else
						nextTask.cancel();
				}
			}
		});
		managerThread.setDaemon(true);
		managerThread.start();
	}


	public void submit(final TaskWithPriority task)
	{
		LOG.trace("Submmiting task {}", task);
		this.queue.add(task);
	}

	public void submit(Runnable r, double priority)
	{
		this.submit(new WrapAsTask(r, priority));
	}

	public void submit(Runnable r)
	{
		this.submit(r, DEFAULT_PRIORITY);
	}

	public void shutdown()
	{
		this.es.shutdown();
	}

	public static void main(String[] args) throws InterruptedException {
		Runnable t1 = MakeUnchecked.runnable(() -> {Thread.sleep(50); System.out.println("t1");});
		Runnable t2 = MakeUnchecked.runnable(() -> {Thread.sleep(50); System.out.println("t2");});
		Runnable t3 = MakeUnchecked.runnable(() -> {Thread.sleep(50); System.out.println("t3");});
		Runnable t4 = MakeUnchecked.runnable(() -> {Thread.sleep(50); System.out.println("t4");});
		final PriorityExecutorService es = new PriorityExecutorService(Executors.newFixedThreadPool(1));
		es.submit(t1, 5.0);
		es.submit(t2, 3.0);
		es.submit(t3, 3.1);
		es.submit(t4, 5.0);
		System.out.println("123");
		Thread.sleep(1000);
	}

}
