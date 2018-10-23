package bdv.fx.viewer;

import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PriorityThreadPoolExecutor {

	interface TaskWithPriority<T> extends Comparable<TaskWithPriority>, Runnable
	{
		double priority();

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

		public WrapAsTask(final Runnable r, final double priority) {
			this.r = r;
			this.priority = priority;
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
		public int compareTo(@NotNull Object o) {
			return 0;
		}
	}

	private final PriorityBlockingQueue<Runnable> queue = new PriorityBlockingQueue<>();

	private final ThreadPoolExecutor executor;

	public PriorityThreadPoolExecutor(
			final int corePoolSize,
			final int maximumPoolSize,
			final long keepAliveTime,
			final TimeUnit unit,
			final ThreadFactory factory
			)
	{
		this.executor = new ThreadPoolExecutor(
				corePoolSize,
				maximumPoolSize,
				keepAliveTime,
				unit,
				queue,
				factory);
	}

	public void execute(final Runnable command, final double priority)
	{
		this.executor.execute(new WrapAsTask(command, priority));
	}

	public static void main(String[] args) {
		Runnable t1 = MakeUnchecked.runnable(() -> {Thread.sleep(50); System.out.println("t1");});
		Runnable t2 = MakeUnchecked.runnable(() -> {Thread.sleep(50); System.out.println("t2");});
		Runnable t3 = MakeUnchecked.runnable(() -> {Thread.sleep(50); System.out.println("t3");});
		Runnable t4 = MakeUnchecked.runnable(() -> {Thread.sleep(50); System.out.println("t4");});
		final PriorityThreadPoolExecutor es = new PriorityThreadPoolExecutor(1, 1, 100_000, TimeUnit.MILLISECONDS, Thread::new);
		es.execute(t1, 0.0);
		es.execute(t2, 0.0);
		es.execute(t3, 0.0);
		es.execute(t4, 0.0);
		System.out.println("123");
	}

}
