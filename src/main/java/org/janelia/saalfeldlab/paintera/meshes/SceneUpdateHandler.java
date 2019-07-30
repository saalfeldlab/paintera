package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import net.imglib2.realtransform.AffineTransform3D;

public class SceneUpdateHandler implements ChangeListener<AffineTransform3D>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final long updateIntervalWhileNavigatingMsec;
	private final long updateDelayAfterNavigatingMsec;
	private final Runnable updateHandler;

	private final Timer timer = new Timer("scene-update-handler", true);
	private TimerTask timerTask = null;
	private long lastUpdateMsec = -1;

	public SceneUpdateHandler(
			final long updateIntervalWhileNavigatingMsec,
			final long updateDelayAfterNavigatingMsec,
			final Runnable updateHandler)
	{
		this.updateIntervalWhileNavigatingMsec = updateIntervalWhileNavigatingMsec;
		this.updateDelayAfterNavigatingMsec = updateDelayAfterNavigatingMsec;
		this.updateHandler = updateHandler;
	}

	@Override
	public synchronized void changed(
			final ObservableValue<? extends AffineTransform3D> observable,
			final AffineTransform3D oldValue,
			final AffineTransform3D newValue)
	{
		if (timerTask != null)
		{
			timerTask.cancel();
			timerTask = null;
		}
		timerTask = new TimerTask()
		{
			@Override
			public void run()
			{
				LOG.debug("Navigation stopped. Update 3D scene");
				synchronized (SceneUpdateHandler.this)
				{
					lastUpdateMsec = -1;
					updateHandler.run();
				}
			}
		};
		timer.schedule(timerTask, updateDelayAfterNavigatingMsec);

		final long msec = System.currentTimeMillis();
		if (lastUpdateMsec == -1)
		{
			lastUpdateMsec = msec;
		}
		else if (msec - lastUpdateMsec >= updateIntervalWhileNavigatingMsec)
		{
			LOG.debug("Navigating... Update 3D scene");
			lastUpdateMsec = msec;
			updateHandler.run();
		}
	}
}
