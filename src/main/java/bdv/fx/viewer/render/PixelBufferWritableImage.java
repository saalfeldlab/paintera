package bdv.fx.viewer.render;

import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.numeric.ARGBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.nio.IntBuffer;

public class PixelBufferWritableImage extends WritableImage {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PixelBuffer<IntBuffer> pixelBuffer;

  public PixelBufferWritableImage(PixelBuffer<IntBuffer> buffer) {

	super(buffer);
	this.pixelBuffer = buffer;
  }

  public static bdv.fx.viewer.render.PixelBufferWritableImage newImage(int width, int height) {

	IntBuffer intBuffer = IntBuffer.allocate(width * height);
	PixelBuffer<IntBuffer> pixelBuffer = new PixelBuffer<>(width, height, intBuffer, PixelFormat.getIntArgbPreInstance());
	return new PixelBufferWritableImage(pixelBuffer);
  }

  private int[] getPixels() {

	return pixelBuffer.getBuffer().array();
  }

  public PixelBuffer<IntBuffer> getPixelBuffer() {

	return pixelBuffer;
  }

  public IntBuffer getBuffer() {

	return pixelBuffer.getBuffer();
  }

  public void setPixelsDirty() {

	this.pixelBuffer.updateBuffer(buffer -> null);
  }

  public ArrayImg<ARGBType, IntAccess> asArrayImg() {

	return ArrayImgs.argbs(new IntArray(getPixels()), (long)getWidth(), (long)getHeight());
  }

}
