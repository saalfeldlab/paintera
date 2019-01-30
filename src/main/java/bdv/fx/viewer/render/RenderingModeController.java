package bdv.fx.viewer.render;

import java.lang.invoke.MethodHandles;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.lang.NotImplementedException;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenderingModeController {

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static enum RenderingMode {
		MULTI_TILE,
		SINGLE_TILE
	}

	private static final int[] DEFAULT_TILE_SIZE = {100, 100};
	private static final long MODE_SWITCH_DELAY = 100;

	private final RenderUnit renderUnit;
	private RenderingMode mode;

	private long lastTransformChangedTime;
	private final Timer modeSwitchTimer;
	private TimerTask modeSwitchTimerTask;

	public RenderingModeController(final RenderUnit renderUnit)
	{
		this.renderUnit = renderUnit;
		this.modeSwitchTimer = new Timer(true);
		setMode(RenderingMode.MULTI_TILE);
	}

	private void setMode(final RenderingMode mode)
	{
		if (mode == this.mode)
			return;

		this.mode = mode;
		LOG.debug("Switching rendering mode to " + mode);

		switch (mode) {
		case MULTI_TILE:
			this.renderUnit.setBlockSize(DEFAULT_TILE_SIZE[0], DEFAULT_TILE_SIZE[1]);
			break;
		case SINGLE_TILE:
			this.renderUnit.setBlockSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
			break;
		default:
			throw new NotImplementedException("Rendering mode " + mode + " is not implemented yet");
		}
	}

	public void transformChanged()
	{
		if (modeSwitchTimerTask != null)
			modeSwitchTimerTask.cancel();

		lastTransformChangedTime = System.currentTimeMillis();
		InvokeOnJavaFXApplicationThread.invoke(() -> setMode(RenderingMode.SINGLE_TILE));
	}

	public void paintingStarted()
	{
		if (mode == RenderingMode.SINGLE_TILE) {
			if (modeSwitchTimerTask != null)
				modeSwitchTimerTask.cancel();

			InvokeOnJavaFXApplicationThread.invoke(() -> {
				setMode(RenderingMode.MULTI_TILE);
				renderUnit.requestRepaint();
			});
		}
	}

	public void receivedRenderedImage(final int screenScaleIndex)
	{
		if (mode == RenderingMode.SINGLE_TILE && screenScaleIndex == 0) {
			final long currentTime = System.currentTimeMillis();
			if (currentTime - lastTransformChangedTime >= MODE_SWITCH_DELAY) {
				InvokeOnJavaFXApplicationThread.invoke(() -> setMode(RenderingMode.MULTI_TILE));
			} else {
				modeSwitchTimerTask = new TimerTask() {
					@Override
					public void run() {
						InvokeOnJavaFXApplicationThread.invoke(() -> setMode(RenderingMode.MULTI_TILE));
					}
				};
				modeSwitchTimer.schedule(modeSwitchTimerTask, MODE_SWITCH_DELAY - Math.max(currentTime - lastTransformChangedTime, 0));
			}
		}
	}
}
