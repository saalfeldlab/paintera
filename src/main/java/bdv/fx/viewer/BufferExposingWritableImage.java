package bdv.fx.viewer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import org.janelia.saalfeldlab.util.MakeUnchecked;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.type.numeric.ARGBType;

public class BufferExposingWritableImage extends WritableImage
{

	private final Method getWritablePlatformImage;

	private final Field pixelBuffer;

	private final Method pixelsDirty;

	private final Field serial;

	private final Buffer buffer;

	private final Runnable callPixelsDirty;

	@SuppressWarnings( "restriction" )
	public BufferExposingWritableImage( final int width, final int height ) throws NoSuchMethodException, SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, InvocationTargetException
	{
		super( width, height );
		this.getWritablePlatformImage = Image.class.getDeclaredMethod( "getWritablePlatformImage" );
		this.getWritablePlatformImage.setAccessible( true );

		this.pixelBuffer = com.sun.prism.Image.class.getDeclaredField( "pixelBuffer" );
		this.pixelBuffer.setAccessible( true );

		this.pixelsDirty = Image.class.getDeclaredMethod( "pixelsDirty" );
		this.pixelsDirty.setAccessible( true );
//
		this.serial = com.sun.prism.Image.class.getDeclaredField( "serial" );
		this.serial.setAccessible( true );

		this.buffer = ( Buffer ) this.pixelBuffer.get( getWritablePlatformImage.invoke( this ) );

		this.callPixelsDirty = MakeUnchecked.unchecked( () -> {
			final com.sun.prism.Image prismImage = ( com.sun.prism.Image ) this.getWritablePlatformImage.invoke( this );
			final int[] serial = ( int[] ) this.serial.get( prismImage );
			serial[ 0 ]++;
			this.pixelsDirty.invoke( this );
		} );

		final com.sun.prism.Image platformImage = ( com.sun.prism.Image ) getWritablePlatformImage.invoke( this );
	}

	public Buffer getBuffer()
	{
		return buffer;
	}

	public void setPixelsDirty()
	{
		this.callPixelsDirty.run();
	}

	public ArrayImg< ARGBType, IntAccess > asArrayImg()
	{
		final ByteBuffer buffer = ( ByteBuffer ) getBuffer();
		return ArrayImgs.argbs( new ByteBufferAccessARGBtoRGBA( buffer ), ( long ) getWidth(), ( long ) getHeight() );
//		return ArrayImgs.argbs( new IntBufferAccess( buffer ), ( long ) getWidth(), ( long ) getHeight() );
	}

}
