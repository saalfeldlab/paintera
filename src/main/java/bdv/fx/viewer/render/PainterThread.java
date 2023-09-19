package bdv.fx.viewer.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.RejectedExecutionException;

public final class PainterThread extends Thread {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final PainterThread.Paintable paintable;

	private boolean pleaseRepaint;

	private boolean isRunning;

	private long lastUpdate = -1;
	private long targetFrameRateMs = 1000 / 60;

	public PainterThread(PainterThread.Paintable paintable) {

		this(null, "PainterThread", paintable);
	}

	public PainterThread(ThreadGroup group, PainterThread.Paintable paintable) {

		this(group, "PainterThread", paintable);
	}

	public PainterThread(ThreadGroup group, String name, PainterThread.Paintable paintable) {

		super(group, name);
		this.paintable = paintable;
		this.pleaseRepaint = false;
		this.isRunning = true;
		this.setDaemon(true);
	}

	@Override
	public void run() {


		while (this.isRunning) {
			final long msSinceLastUpdate = System.currentTimeMillis() - this.lastUpdate;
			if (lastUpdate > 0 && msSinceLastUpdate < targetFrameRateMs) {
				try {
					Thread.sleep(msSinceLastUpdate);
				} catch (InterruptedException e) {
				}
			}
			if (this.isRunning && !this.isInterrupted()) {
				boolean b;
				synchronized (this) {
					b = this.pleaseRepaint;
					this.pleaseRepaint = false;
				}

				if (b) {
					try {
						lastUpdate = System.currentTimeMillis();
						this.paintable.paint();
					} catch (RejectedExecutionException var5) {
					}
				}

				synchronized (this) {
					try {
						if (this.isRunning && !this.pleaseRepaint) {
							this.wait();
						}
						continue;
					} catch (InterruptedException var7) {
					}
				}
			}

			return;
		}
	}

	public void requestRepaint() {

		synchronized (this) {
			this.pleaseRepaint = true;
			this.notify();
		}
	}

	public interface Paintable {

		void paint();
	}

	public void stopRendering() {

		synchronized (this) {
			LOG.debug("Stop rendering now!");
			this.isRunning = false;
			this.notify();
			LOG.debug("Notified on this ({})", this);
		}
	}

}
