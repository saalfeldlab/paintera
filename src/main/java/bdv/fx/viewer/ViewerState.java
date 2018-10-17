package bdv.fx.viewer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import bdv.util.MipmapTransforms;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;

public class ViewerState
{

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	protected final IntegerProperty timepoint = new SimpleIntegerProperty(0);

	protected final IntegerProperty numTimepoints = new SimpleIntegerProperty(1);

	protected final ObservableList<SourceAndConverter<?>> sourcesAndConverters = FXCollections.observableArrayList();

	private final Function<Source<?>, AxisOrder> axisOrder;

	protected final ObservableMap<Source<?>, SourceAndConverter<?>> sources = asMap(
			sourcesAndConverters,
			SourceAndConverter::getSpimSource
	                                                                               );

	protected void setViewerTransform(final AffineTransform3D to)
	{
		this.viewerTransform.set(to);
	}

	public void getViewerTransform(final AffineTransform3D to)
	{
		to.set(this.viewerTransform);
	}

	public ReadOnlyIntegerProperty timepointProperty()
	{
		return this.timepoint;
	}

	public List<SourceAndConverter<?>> getSources()
	{
		return Collections.unmodifiableList(sourcesAndConverters);
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

	public ViewerState(final Function<Source<?>, AxisOrder> axisOrder)
	{
		this.axisOrder = axisOrder;
	}

	public ViewerState copy()
	{
		final ViewerState state = new ViewerState(this.axisOrder);
		state.viewerTransform.set(viewerTransform);
		state.timepoint.set(timepoint.get());
		state.numTimepoints.set(numTimepoints.get());
		state.sourcesAndConverters.setAll(sourcesAndConverters);
		return state;
	}

	public static <S, T> ObservableList<T> mapObservableList(final ObservableList<? extends S> source, final
	Function<S, T> mapping)
	{
		final ObservableList<T> target = FXCollections.observableArrayList();
		source.addListener((ListChangeListener<? super S>) change -> target.setAll(source.stream().map(mapping)
				.collect(
				Collectors.toList())));
		return target;
	}

	public static <S, T> ObservableMap<T, S> asMap(final ObservableList<? extends S> source, final Function<S, T>
			generateKeyFromValue)
	{
		final ObservableMap<T, S> target = FXCollections.observableHashMap();
		source.addListener((ListChangeListener<? super S>) change -> {
			final Map<T, S> tmp = new HashMap<>();
			source.forEach(s -> tmp.put(generateKeyFromValue.apply(s), s));
			target.putAll(tmp);
		});
		return target;
	}

	public AxisOrder axisOrder(final Source<?> source)
	{
		return this.axisOrder.apply(source);
	}

}
