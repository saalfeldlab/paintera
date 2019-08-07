package bdv.fx.viewer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;

import bdv.util.MipmapTransforms;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * Notifies the listeners whenever the list of sources is changed.
 */
public class ViewerState extends ObservableWithListenersList
{

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	protected final IntegerProperty timepoint = new SimpleIntegerProperty(0);

	protected final IntegerProperty numTimepoints = new SimpleIntegerProperty(1);

	protected final List<SourceAndConverter<?>> sourcesAndConverters = new ArrayList<>();

	private final Function<Source<?>, AxisOrder> axisOrder;

	public ViewerState(final Function<Source<?>, AxisOrder> axisOrder)
	{
		this.axisOrder = axisOrder;
	}

	protected synchronized void setViewerTransform(final AffineTransform3D to)
	{
		this.viewerTransform.set(to);
	}

	public synchronized void getViewerTransform(final AffineTransform3D to)
	{
		to.set(this.viewerTransform);
	}

	public synchronized int getTimepoint()
	{
		return this.timepoint.get();
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
		return getBestMipMapLevel(screenScaleTransform, source, timepoint.get());
	}

	public synchronized int getBestMipMapLevel(final AffineTransform3D screenScaleTransform, final int sourceIndex)
	{
		return getBestMipMapLevel(screenScaleTransform, sourcesAndConverters.get(sourceIndex).getSpimSource());
	}

	public synchronized ViewerState copy()
	{
		final ViewerState state = new ViewerState(this.axisOrder);
		state.viewerTransform.set(viewerTransform);
		state.timepoint.set(timepoint.get());
		state.numTimepoints.set(numTimepoints.get());
		state.setSources(sourcesAndConverters);
		return state;
	}

	public synchronized AxisOrder axisOrder(final Source<?> source)
	{
		return this.axisOrder.apply(source);
	}
}
