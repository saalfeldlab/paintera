package bdv.fx.viewer.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.imglib2.Interval;
import net.imglib2.util.Intervals;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.RejectedExecutionException;

public final class PainterThread extends Thread {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public interface Paintable {
		void paint(Interval interval);
	}
	
	private static final int MAX_PIXELS_IN_INTERVAL = 200 * 200;

	private final PainterThread.Paintable paintable;

	private final Deque<Interval> repaintRequests;

	private boolean isRunning;

	public PainterThread(PainterThread.Paintable paintable) {
		this((ThreadGroup)null, "PainterThread", paintable);
	}

	public PainterThread(ThreadGroup group, PainterThread.Paintable paintable) {
		this(group, "PainterThread", paintable);
	}

	public PainterThread(ThreadGroup group, String name, PainterThread.Paintable paintable) {
		super(group, name);
		this.paintable = paintable;
		this.repaintRequests = new ArrayDeque<>();
		this.isRunning = true;
		this.setDaemon(true);
	}

	public void run() {
		while(this.isRunning) {
			if (this.isRunning && !this.isInterrupted()) {
				final Interval repaintRequest;
				synchronized(this) {
					Interval mergedInterval = this.repaintRequests.pollFirst();
					if (mergedInterval != null) {
						while (!this.repaintRequests.isEmpty()) {
							final Interval newMergedInterval = Intervals.union(this.repaintRequests.peekFirst(), mergedInterval);
							if (Intervals.numElements(newMergedInterval) >= MAX_PIXELS_IN_INTERVAL)
								break;
							mergedInterval = newMergedInterval;
							this.repaintRequests.removeFirst();
						}
						System.out.println("got repaint request at " + Arrays.toString(Intervals.minAsLongArray(mergedInterval)) + " of size " + Arrays.toString(Intervals.dimensionsAsLongArray(mergedInterval)) + ", pending: " + getNumPendingRequests());
					}
					repaintRequest = mergedInterval;
				}

				if (repaintRequest != null) {
					try {
						this.paintable.paint(repaintRequest);
					} catch (RejectedExecutionException var5) {
						;
					}
				}

				synchronized(this) {
					try {
						if (this.isRunning && this.repaintRequests.isEmpty()) {
							this.wait();
						}
						continue;
					} catch (InterruptedException var7) {
						;
					}
				}
			}
		}
	}

	public synchronized int getNumPendingRequests()
	{
		return this.repaintRequests.size();
	}

	public synchronized void requestRepaint(final Interval interval)
	{
		// in case there are pending requests, check if the new interval is contained in the most recently added one
		if (this.repaintRequests.isEmpty() || !Intervals.contains(this.repaintRequests.peekLast(), interval)) {
			this.repaintRequests.addLast(interval);
			this.notify();
		}
		else if (Intervals.contains(interval, this.repaintRequests.peekLast()))
		{
			// or, replace the most recently added interval with the new one if the new one fully contains the last one
			this.repaintRequests.removeLast();
			this.repaintRequests.addLast(interval);
			this.notify();
		}
	}

	public synchronized void stopRendering()
	{
		LOG.debug("Stop rendering now!");
		this.isRunning = false;
		this.notify();
		LOG.debug("Notified on this ({})", this);
	}
}
