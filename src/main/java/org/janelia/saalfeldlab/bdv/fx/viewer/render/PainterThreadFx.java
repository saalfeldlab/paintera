package org.janelia.saalfeldlab.bdv.fx.viewer.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.RejectedExecutionException;

public final class PainterThreadFx extends Thread {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final PainterThreadFx.Paintable paintable;

	private boolean pleaseRepaint;

	private boolean isRunning;

	private long lastUpdate = -1;
	private long targetFrameRateMs = 1000 / 60;

	public PainterThreadFx(PainterThreadFx.Paintable paintable) {

		this(null, "PainterThread", paintable);
	}

	public PainterThreadFx(ThreadGroup group, PainterThreadFx.Paintable paintable) {

		this(group, "PainterThread", paintable);
	}

	public PainterThreadFx(ThreadGroup group, String name, PainterThreadFx.Paintable paintable) {

		super(group, name);
		this.paintable = paintable;
		this.pleaseRepaint = false;
		this.isRunning = true;
		this.setDaemon(true);
	}

	@Override
	public void run() {


		while (this.isRunning) {
			boolean paint;
			synchronized (this) {
				paint = pleaseRepaint;
				pleaseRepaint = false;
			}

			if (paint) {
				try {
					paintable.paint();
				} catch (RejectedExecutionException var5) {
				}
			}

			synchronized (this) {
				try {
					if (isRunning && !pleaseRepaint) {
						wait();
					}
					if (isRunning)
						continue;
				} catch (InterruptedException var7) {
					System.out.println(Thread.currentThread().getName() + " interrupted");
				}
			}
			return;
		}
	}

	public void requestRepaint() {

		synchronized (this) {
			pleaseRepaint = true;
			notify();
		}
	}

	public interface Paintable {

		void paint();
	}

	public void stopRendering() {

		LOG.debug("Stop rendering now!");
		isRunning = false;
		interrupt();
		LOG.debug("Notified on this ({})", this);
	}

}
