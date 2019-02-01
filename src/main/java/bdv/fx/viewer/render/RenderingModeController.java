package bdv.fx.viewer.render;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.NotImplementedException;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenderingModeController {

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static enum RenderingMode {
		MULTI_TILE,
		SINGLE_TILE
	}

	private static final int[] DEFAULT_TILE_SIZE = {200, 200};

	private final RenderUnit renderUnit;
	private RenderingMode mode;
	private boolean isPainting;

	private final AtomicInteger currentTag = new AtomicInteger();
	private int lastReceivedTag;

	public RenderingModeController(final RenderUnit renderUnit)
	{
		this.renderUnit = renderUnit;
		setMode(RenderingMode.MULTI_TILE);
	}

	private void setMode(final RenderingMode mode)
	{
		if (mode == this.mode)
			return;

		this.mode = mode;
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
		return !isPainting || tag == currentTag.get();
	}

	public void transformChanged()
	{
		currentTag.incrementAndGet();
		if (mode != RenderingMode.SINGLE_TILE) {
			System.out.println("Navigation has been initiated");
			InvokeOnJavaFXApplicationThread.invoke(() -> setMode(RenderingMode.SINGLE_TILE));
		}
	}

	public void paintingStarted()
	{
		isPainting = true;
		final int tag = currentTag.getAndIncrement();
		if (mode != RenderingMode.MULTI_TILE) {
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
		isPainting = false;
		currentTag.incrementAndGet();
//		InvokeOnJavaFXApplicationThread.invoke(() -> setMode(RenderingMode.SINGLE_TILE));
	}

	public void receivedRenderedImage(final int tag)
	{
		lastReceivedTag = tag;
	}
}
