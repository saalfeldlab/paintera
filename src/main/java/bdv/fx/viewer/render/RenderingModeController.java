package bdv.fx.viewer.render;

import java.lang.invoke.MethodHandles;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;

public class RenderingModeController implements TransformListener<AffineTransform3D> {

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static enum RenderingMode {
		MULTI_TILE,
		SINGLE_TILE
	}

	private static final int[] DEFAULT_TILE_SIZE = {250, 250};

	private final RenderUnit renderUnit;
	private RenderingMode mode;

	private final int modeSwitchDelay = 250;
	private final Timer modeSwitchTimer;
	private TimerTask modeSwitchTimerTask;

	public RenderingModeController(final RenderUnit renderUnit)
	{
		this.renderUnit = renderUnit;
		this.modeSwitchTimer = new Timer(true);
		setMode(RenderingMode.MULTI_TILE);
	}

	public void setMode(final RenderingMode mode)
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

	@Override
	public void transformChanged(AffineTransform3D transform)
	{
		setMode(RenderingMode.SINGLE_TILE);

		if (modeSwitchTimerTask != null)
			modeSwitchTimerTask.cancel();

		modeSwitchTimerTask = new TimerTask() {

			@Override
			public void run() {
				setMode(RenderingMode.MULTI_TILE);
			}
		};

		modeSwitchTimer.schedule(modeSwitchTimerTask, modeSwitchDelay);
	}
}
