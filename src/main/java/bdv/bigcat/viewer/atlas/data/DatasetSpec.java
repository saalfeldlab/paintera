package bdv.bigcat.viewer.atlas.data;

import java.util.Optional;

import bdv.viewer.Source;

public interface DatasetSpec< DataType, ViewerType >
{
	public Source< DataType > getSource();

	public Source< ViewerType > getViewerSource();

	public String name();

	public default Optional< String > uri()
	{
		return Optional.empty();
	}
}
