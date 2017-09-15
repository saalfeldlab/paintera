package bdv.bigcat.viewer.atlas.data;

import bdv.viewer.Source;

public interface DatasetSpec< DataType, ViewerType >
{
	public Source< DataType > getSource();

	public Source< ViewerType > getViewerSource();
}
