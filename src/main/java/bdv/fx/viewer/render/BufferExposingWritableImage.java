package bdv.fx.viewer.render;

import javafx.scene.image.Image;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.numeric.ARGBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.nio.IntBuffer;

public class BufferExposingWritableImage
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final int[] store;

	private final PixelBuffer<IntBuffer> pixelBuffer;

	private final WritableImage image;

	public BufferExposingWritableImage(final int width, final int height)
	{
		this.store = new int[width * height];
		final IntBuffer buffer = IntBuffer.wrap(this.store);
		final PixelFormat<IntBuffer> pixelFormat = PixelFormat.getIntArgbPreInstance();
		this.pixelBuffer = new PixelBuffer<>(width, height, buffer, pixelFormat);
		this.image = new WritableImage(this.pixelBuffer);
	}

	public void setPixelsDirty()
	{
		this.pixelBuffer.updateBuffer(val -> null);
	}

	public Image getImage()
	{
		return this.image;
	}

	public ArrayImg<ARGBType, IntArray> asArrayImg()
	{
		return ArrayImgs.argbs(store, (long) image.getWidth(), (long) image.getHeight());
	}
}
