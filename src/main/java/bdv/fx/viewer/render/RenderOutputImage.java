package bdv.fx.viewer.render;

import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.type.numeric.ARGBType;

public interface RenderOutputImage<T> {

	int width();

	int height();

	ArrayImg<ARGBType, IntAccess> asArrayImg();

	T unwrap();

	interface Factory<T> {

		RenderOutputImage< T > create( int width, int height );

		RenderOutputImage< T > create( int width, int heihgt, RenderOutputImage< T > other);
	}
}
