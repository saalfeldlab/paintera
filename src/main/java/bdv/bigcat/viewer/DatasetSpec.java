package bdv.bigcat.viewer;

import bdv.viewer.Source;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public interface DatasetSpec< DataType, ViewerType >
{
	public Source< DataType > getSource();

	public Source< ViewerType > getViewerSource();

	public Converter< ViewerType, ARGBType > getViewerConverter();
}
