package bdv.fx.viewer;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.sun.javafx.tk.PlatformImage;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BufferExposingWritableImage extends WritableImage
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Method setWritablePlatformImage;

	private final Method pixelsDirty;

	private final Field serial;

	private final Runnable callPixelsDirty;

	private final int[] store;

	private final com.sun.prism.Image prismImage;

	@SuppressWarnings("restriction")
	public BufferExposingWritableImage(final int width, final int height) throws
			NoSuchMethodException,
			SecurityException,
			NoSuchFieldException,
			IllegalArgumentException,
			IllegalAccessException,
			InvocationTargetException
	{
		super(width, height);

		this.setWritablePlatformImage = Image.class.getDeclaredMethod("setPlatformImage", PlatformImage.class);
		this.setWritablePlatformImage.setAccessible(true);

		this.store = new int[width * height];
		this.prismImage = com.sun.prism.Image.fromIntArgbPreData(store, width, height);
		this.setWritablePlatformImage.invoke(this, prismImage);

		this.pixelsDirty = Image.class.getDeclaredMethod("pixelsDirty");
		this.pixelsDirty.setAccessible(true);

		this.serial = com.sun.prism.Image.class.getDeclaredField("serial");
		this.serial.setAccessible(true);

		this.callPixelsDirty = MakeUnchecked.runnable(() -> {
			final int[] serial = (int[]) this.serial.get(prismImage);
			serial[0]++;
			this.pixelsDirty.invoke(this);
		});

		LOG.debug(
				"Got pixelformat={} and platform pixel format={}",
				prismImage.getPixelFormat(),
				prismImage.getPlatformPixelFormat()
		         );
	}

	public void setPixelsDirty()
	{
		this.callPixelsDirty.run();
	}

	public ArrayImg<ARGBType, IntAccess> asArrayImg()
	{
		return ArrayImgs.argbs(new IntArray(store), (long) getWidth(), (long) getHeight());
	}

}
