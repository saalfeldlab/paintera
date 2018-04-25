package bdv.fx.viewer;

import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.type.numeric.ARGBType;

public class WrapAsAccess implements IntAccess
{

	private final WritableImage data;

	private final int width;

	private final int height;

	private final PixelWriter writer;

	private final PixelReader reader;

	public WrapAsAccess( final WritableImage data )
	{
		super();
		this.data = data;
		this.width = ( int ) data.getWidth();
		this.height = ( int ) data.getHeight();
		this.writer = data.getPixelWriter();
		this.reader = data.getPixelReader();
	}

	@Override
	public int getValue( final int index )
	{
		final int y = index / width;
		final int x = index - y * width;
		return reader.getArgb( x, y );
	}

	@Override
	public void setValue( final int index, final int value )
	{
		final int y = index / width;
		final int x = index - y * width;
		this.writer.setArgb( x, y, value );
	}

	public static ArrayImg< ARGBType, WrapAsAccess > wrap( final WritableImage img )
	{
		return ArrayImgs.argbs( new WrapAsAccess( img ), ( long ) img.getWidth(), ( long ) img.getHeight() );
	}

}
