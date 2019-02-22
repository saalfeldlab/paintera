package bdv.fx.viewer.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.imglib2.Interval;
import net.imglib2.util.Intervals;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.RejectedExecutionException;

public final class PainterThread extends Thread {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public interface Paintable {
		void paint(Interval interval);
	}

	private static final int MAX_PIXELS_IN_INTERVAL = 200 * 200;

	private final PainterThread.Paintable paintable;

	private final Deque<Interval> repaintRequestsLowRes, repaintRequestsHigherRes;

	private volatile boolean isRunning;

	public PainterThread(final PainterThread.Paintable paintable) {
		this(null, "PainterThread", paintable);
	}

	public PainterThread(final ThreadGroup group, final PainterThread.Paintable paintable) {
		this(group, "PainterThread", paintable);
	}

	public PainterThread(final ThreadGroup group, final String name, final PainterThread.Paintable paintable) {
		super(group, name);
		this.paintable = paintable;
		this.repaintRequestsLowRes = new ArrayDeque<>();
		this.repaintRequestsHigherRes = new ArrayDeque<>();
		this.isRunning = true;
		this.setDaemon(true);
	}

	public void run() {
		while(this.isRunning) {
			if (this.isRunning && !this.isInterrupted()) {
				final Deque<Interval> repaintRequests = !this.repaintRequestsLowRes.isEmpty() ? this.repaintRequestsLowRes : this.repaintRequestsHigherRes;
				final Interval repaintRequest;
				synchronized(this) {
					Interval mergedInterval = repaintRequests.pollFirst();
					if (mergedInterval != null) {
						int numMerged = 1;
						while (!repaintRequests.isEmpty()) {
							final Interval newMergedInterval = Intervals.union(repaintRequests.peekFirst(), mergedInterval);
							if (Intervals.numElements(newMergedInterval) >= MAX_PIXELS_IN_INTERVAL)
								break;
							mergedInterval = newMergedInterval;
							++numMerged;
							repaintRequests.removeFirst();
						}
//						System.out.println(" ******** merged: " + numMerged + ".  got repaint request at " + Arrays.toString(Intervals.minAsLongArray(mergedInterval)) + " of size " + Arrays.toString(Intervals.dimensionsAsLongArray(mergedInterval)) + ",   pending: lowres=" + this.repaintRequestsLowRes.size() + ", highres=" + this.repaintRequestsHigherRes.size() + " ******** ");
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
						if (this.isRunning && getNumPendingRequests() == 0) {
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
		return this.repaintRequestsLowRes.size() + this.repaintRequestsHigherRes.size();
	}

	public synchronized void requestRepaintLowRes(final Interval interval)
	{
		requestRepaint(interval, this.repaintRequestsLowRes);
	}

	public synchronized void requestRepaintHigherRes(final Interval interval)
	{
		requestRepaint(interval, this.repaintRequestsHigherRes);
	}

	private synchronized void requestRepaint(final Interval interval, final Deque<Interval> repaintRequests)
	{
		// in case there are pending requests, check if the new interval is contained in the most recently added one
		if (repaintRequests.isEmpty() || !Intervals.contains(repaintRequests.peekLast(), interval)) {
//			if (!repaintRequests.isEmpty())
//				System.out.println("last insterval: min=" + Arrays.toString(Intervals.minAsLongArray(repaintRequests.peekLast())) + ",size=" + Arrays.toString(Intervals.dimensionsAsLongArray(repaintRequests.peekLast())) + ",  new insterval: min=" + Arrays.toString(Intervals.minAsLongArray(interval)) + ",size=" + Arrays.toString(Intervals.dimensionsAsLongArray(interval)));
			repaintRequests.addLast(interval);
			this.notify();
		}
		else if (Intervals.contains(interval, repaintRequests.peekLast()))
		{
			// or, replace the most recently added interval with the new one if the new one fully contains the last one
			repaintRequests.removeLast();
			repaintRequests.addLast(interval);
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
