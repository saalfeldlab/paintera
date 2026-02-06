package org.janelia.saalfeldlab.bdv.fx.viewer.render;

import io.github.oshai.kotlinlogging.KLogger;
import io.github.oshai.kotlinlogging.KotlinLogging;
import kotlin.Unit;

import java.util.concurrent.RejectedExecutionException;

public final class PainterThreadFx extends Thread {

	private static final KLogger LOG = KotlinLogging.INSTANCE.logger(() -> Unit.INSTANCE);

	private final PainterThreadFx.Paintable paintable;

	private boolean pleaseRepaint;

	private boolean isRunning;


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
					LOG.trace(() -> "Painter thread interrupted: " + Thread.currentThread().getName());
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

		LOG.debug(() -> "Stop rendering now!");
		isRunning = false;
		interrupt();
		LOG.debug(() -> String.format("Notified on this (%s)", this));
	}

}
