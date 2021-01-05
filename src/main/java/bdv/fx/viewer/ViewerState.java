package bdv.fx.viewer;

import bdv.util.MipmapTransforms;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.fx.ObservableWithListenersList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ViewerState extends ObservableWithListenersList
{

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	private final List<SourceAndConverter<?>> sourcesAndConverters = new ArrayList<>();

	private final int numTimepoints;

	private int timepoint;

	public ViewerState(final int numTimepoints)
	{
		this.numTimepoints = numTimepoints;
	}

	protected void setViewerTransform(final AffineTransform3D to)
	{
		synchronized (this)
		{
			this.viewerTransform.set(to);
		}
		stateChanged();
	}

	public synchronized void getViewerTransform(final AffineTransform3D to)
	{
		to.set(this.viewerTransform);
	}

	public void setTimepoint(final int timepoint)
	{
		synchronized (this)
		{
			this.timepoint = timepoint;
		}
		stateChanged();
	}

	public synchronized int getTimepoint()
	{
		return this.timepoint;
	}

	public synchronized List<SourceAndConverter<?>> getSources()
	{
		return Collections.unmodifiableList(sourcesAndConverters);
	}

	public void setSources(final Collection<? extends SourceAndConverter<?>> newSources)
	{
		synchronized (this)
		{
			this.sourcesAndConverters.clear();
			this.sourcesAndConverters.addAll(newSources);
		}
		stateChanged();
	}

	public synchronized int getBestMipMapLevel(final AffineTransform3D screenScaleTransform, final Source<?> source,
	                                           final int timepoint)
	{
		final AffineTransform3D screenTransform = new AffineTransform3D();
		getViewerTransform(screenTransform);
		screenTransform.preConcatenate(screenScaleTransform);

		return MipmapTransforms.getBestMipMapLevel(screenTransform, source, timepoint);
	}

	public synchronized int getBestMipMapLevel(final AffineTransform3D screenScaleTransform, final Source<?> source)
	{
		return getBestMipMapLevel(screenScaleTransform, source, timepoint);
	}

	public synchronized int getBestMipMapLevel(final AffineTransform3D screenScaleTransform, final int sourceIndex)
	{
		return getBestMipMapLevel(screenScaleTransform, sourcesAndConverters.get(sourceIndex).getSpimSource());
	}

	public synchronized ViewerState copy()
	{
		final ViewerState state = new ViewerState(this.numTimepoints);
		state.setViewerTransform(this.viewerTransform);
		state.setTimepoint(this.timepoint);
		state.setSources(this.sourcesAndConverters);
		return state;
	}

}
