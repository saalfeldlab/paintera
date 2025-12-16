package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Timer;
import java.util.TimerTask;

public class SceneUpdateHandler implements ChangeListener<AffineTransform3D> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private long updateDelayAfterNavigatingMsec = Viewer3DConfig.SCENE_UPDATE_DELAY_MSEC_DEFAULT_VALUE;
	private long updateIntervalWhileNavigatingMsec = -1; // currently not used and not exposed the UI

	private final Runnable updateHandler;
	private final Timer timer = new Timer("scene-update-handler", true);
	private TimerTask timerTask = null;
	private long lastUpdateMsec = -1;

	public SceneUpdateHandler(final Runnable updateHandler) {

		this.updateHandler = updateHandler;
	}

	public synchronized void setNavigationUpdateDelay(final long updateDelayMsec) {

		this.updateDelayAfterNavigatingMsec = updateDelayMsec;
	}

	@Override
	public synchronized void changed(
			final ObservableValue<? extends AffineTransform3D> observable,
			final AffineTransform3D oldValue,
			final AffineTransform3D newValue) {

		if (timerTask != null) {
			timerTask.cancel();
		}
		timerTask = new TimerTask() {

			@Override
			public void run() {

				LOG.debug("Navigation stopped. Update 3D scene");
				synchronized (SceneUpdateHandler.this) {
					lastUpdateMsec = -1;
					updateHandler.run();
				}
			}
		};
		timer.schedule(timerTask, updateDelayAfterNavigatingMsec);

		final long msec = System.currentTimeMillis();
		if (lastUpdateMsec == -1) {
			lastUpdateMsec = msec;
		} else if (updateIntervalWhileNavigatingMsec != -1 && msec - lastUpdateMsec >= updateIntervalWhileNavigatingMsec) {
			LOG.debug("Navigating... Update 3D scene");
			lastUpdateMsec = msec;
			updateHandler.run();
		}
	}
}
