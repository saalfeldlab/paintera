//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package bdv.fx.viewer;

import java.util.concurrent.RejectedExecutionException;

public final class PainterThread extends Thread {
	private final PainterThread.Paintable paintable;
	private boolean pleaseRepaint;
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
		this.pleaseRepaint = false;
		this.isRunning = true;
		this.setDaemon(true);
	}

	public void run() {
		while(this.isRunning) {
			if (this.isRunning && !this.isInterrupted()) {
				boolean b;
				synchronized(this) {
					b = this.pleaseRepaint;
					this.pleaseRepaint = false;
				}

				if (b) {
					try {
						this.paintable.paint();
					} catch (RejectedExecutionException var5) {
						;
					}
				}

				synchronized(this) {
					try {
						if (!this.pleaseRepaint) {
							this.wait();
						}
						continue;
					} catch (InterruptedException var7) {
						;
					}
				}
			}

			return;
		}
	}

	public void requestRepaint() {
		synchronized(this) {
			this.pleaseRepaint = true;
			this.notify();
		}
	}

	public interface Paintable {
		void paint();
	}

	public void stopRendering()
	{
		synchronized(this) {
			this.isRunning = false;
			this.notify();
		}
	}

}
