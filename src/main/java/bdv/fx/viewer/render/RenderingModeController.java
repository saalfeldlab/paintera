package bdv.fx.viewer.render;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.NotImplementedException;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class RenderingModeController {

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static enum RenderingMode {
		MULTI_TILE,
		SINGLE_TILE
	}

	private static final int[] DEFAULT_TILE_SIZE = {200, 200};

	private final RenderUnit renderUnit;
	private final ObjectProperty<RenderingMode> modeProperty = new SimpleObjectProperty<>();

	private final AtomicInteger currentTag = new AtomicInteger();
	private int lastReceivedTag;
	private int lastModeSwitchTag;

	public RenderingModeController(final RenderUnit renderUnit)
	{
		this.renderUnit = renderUnit;
		setMode(RenderingMode.MULTI_TILE);
	}

	public ObjectProperty<RenderingMode> getModeProperty()
	{
		return modeProperty;
	}

	private void setMode(final RenderingMode mode)
	{
		if (mode == modeProperty.get())
			return;

		modeProperty.set(mode);
		lastModeSwitchTag = currentTag.get();
		System.out.println("Switching rendering mode to " + mode);

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

	public int getCurrentTag()
	{
		return currentTag.get();
	}

	public boolean validateTag(final int tag)
	{
		return tag >= lastModeSwitchTag;
	}

	public void transformChanged()
	{
		currentTag.getAndIncrement();
		if (modeProperty.get() != RenderingMode.SINGLE_TILE) {
			System.out.println("Navigation has been initiated");
			InvokeOnJavaFXApplicationThread.invoke(() -> setMode(RenderingMode.SINGLE_TILE));
		}
	}

	public void paintingStarted()
	{
		final int tag = currentTag.getAndIncrement();
		if (modeProperty.get() != RenderingMode.MULTI_TILE) {
			final boolean needRepaint = lastReceivedTag != tag;
			if (needRepaint)
				System.out.println("=========== Have not received rendered image yet after last transform ===========");
			System.out.println("Painting has been initiated");
			InvokeOnJavaFXApplicationThread.invoke(() -> {
				setMode(RenderingMode.MULTI_TILE);
				if (needRepaint)
					renderUnit.requestRepaint();
			});
		}
	}

	public void paintingFinished()
	{
		System.out.println("Painting has been stopped");
		currentTag.incrementAndGet();
	}

	public void receivedRenderedImage(final int tag)
	{
		lastReceivedTag = tag;
	}
}
