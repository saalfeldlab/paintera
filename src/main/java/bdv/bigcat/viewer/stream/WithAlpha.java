package bdv.bigcat.viewer.stream;

import javafx.beans.property.IntegerProperty;

public interface WithAlpha
{

	public IntegerProperty alphaProperty();

	public IntegerProperty activeSegmentAlphaProperty();

	public IntegerProperty activeFragmentAlphaProperty();

}
