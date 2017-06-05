package bdv.bigcat.viewer.source;

import bdv.AbstractViewerSetupImgLoader;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetType;

public interface LabelSource extends Source< LabelMultisetType, VolatileLabelMultisetType >
{

	@Override
	public AbstractViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType > loader();
}
