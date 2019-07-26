package bdv.fx.viewer.render;

import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.type.numeric.ARGBType;

public interface RenderOutputImage {

	int width();

	int height();

	ArrayImg<ARGBType, IntAccess> asArrayImg();

	interface Factory<T> {

		RenderOutputImage create( int width, int height );

		RenderOutputImage create( int width, int heihgt, RenderOutputImage other);

		RenderOutputImage wrap(T image);

		T unwrap(RenderOutputImage image);
	}
}
