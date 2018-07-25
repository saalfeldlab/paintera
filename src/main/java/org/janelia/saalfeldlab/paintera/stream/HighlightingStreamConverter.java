package org.janelia.saalfeldlab.paintera.stream;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

import gnu.trove.map.TLongIntMap;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;
import net.imglib2.converter.Converter;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import org.janelia.saalfeldlab.util.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HighlightingStreamConverter<T>
		implements Converter<T, ARGBType>, SeedProperty, WithAlpha, ColorFromSegmentId, HideLockedSegments,
		           UserSpecifiedColors
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	protected final AbstractHighlightingARGBStream stream;

	private final LongProperty seed = new SimpleLongProperty(1);

	private final IntegerProperty alpha = new SimpleIntegerProperty();

	private final IntegerProperty activeFragmentAlpha = new SimpleIntegerProperty();

	private final IntegerProperty activeSegmentAlpha = new SimpleIntegerProperty();

	private final BooleanProperty colorFromSegmentId = new SimpleBooleanProperty(true);

	private final BooleanProperty hideLockedSegments = new SimpleBooleanProperty(true);

	private final ObservableMap<Long, Color> userSpecifiedColors = FXCollections.observableHashMap();

	private final ObservableMap<Long, Color> unmodifiableSpecifiedColors = FXCollections.unmodifiableObservableMap(
			userSpecifiedColors);

	private final InvalidationListener updateUserSpecifiedColors = new UpdateUserSpecifiedColors();

	public HighlightingStreamConverter(final AbstractHighlightingARGBStream stream)
	{
		super();
		this.stream = stream;
		seed.addListener((obs, oldv, newv) -> stream.setSeed(newv.longValue()));
		alpha.addListener((obs, oldv, newv) -> stream.setAlpha(newv.intValue()));
		activeFragmentAlpha.addListener((obs, oldv, newv) -> stream.setActiveFragmentAlpha(newv.intValue()));
		activeSegmentAlpha.addListener((obs, oldv, newv) -> stream.setActiveSegmentAlpha(newv.intValue()));
		hideLockedSegments.addListener((obs, oldv, newv) -> stream.setHideLockedSegments(newv));
		stream.setSeed(seed.get());
		alpha.set(stream.getAlpha());
		activeFragmentAlpha.set(stream.getActiveFragmentAlpha());
		activeSegmentAlpha.set(stream.getActiveSegmentAlpha());
		hideLockedSegments.set(stream.getHideLockedSegments());
		stream.colorFromSegmentIdProperty().bind(this.colorFromSegmentId);
		stream.addListener(obs -> {
			this.seed.set(stream.getSeed());
			this.alpha.set(stream.getAlpha());
			this.activeFragmentAlpha.set(stream.getActiveFragmentAlpha());
			this.activeSegmentAlpha.set(stream.getActiveSegmentAlpha());
			this.colorFromSegmentId.set(stream.getColorFromSegmentId());
			this.hideLockedSegments.set(stream.getHideLockedSegments());
		});
		stream.addListener(updateUserSpecifiedColors);
	}

	@Override
	public LongProperty seedProperty()
	{
		return this.seed;
	}

	@Override
	public IntegerProperty alphaProperty()
	{
		return this.alpha;
	}

	@Override
	public IntegerProperty activeFragmentAlphaProperty()
	{
		return this.activeFragmentAlpha;
	}

	@Override
	public IntegerProperty activeSegmentAlphaProperty()
	{
		return this.activeSegmentAlpha;
	}

	@Override
	public BooleanProperty colorFromSegmentIdProperty()
	{
		return this.colorFromSegmentId;
	}

	@Override
	public BooleanProperty hideLockedSegmentsProperty()
	{
		return this.hideLockedSegments;
	}

	public AbstractHighlightingARGBStream getStream()
	{
		return this.stream;
	}

	@SuppressWarnings("unchecked")
	public static <T> HighlightingStreamConverter<T> forType(
			final AbstractHighlightingARGBStream stream,
			final T t)
	{
		if (t instanceof IntegerType<?>) {
			return (org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter<T>)
					HighlightingStreamConverterIntegerType.forInteger(
					stream);
		}
		if (t instanceof RealType<?>) {
			return (org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter<T>)
					HighlightingStreamConverterIntegerType.forRealType(
					stream);
		}
		if (t instanceof LabelMultisetType || t instanceof VolatileLabelMultisetType) {
			return (org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter<T>) new
					HighlightingStreamConverterLabelMultisetType(
					stream);
		}

		return null;

	}

	private class UpdateUserSpecifiedColors implements InvalidationListener
	{

		@Override
		public void invalidated(final Observable observable)
		{
			final Map<Long, Color> map                       = new HashMap<>();
			final TLongIntMap      explicitlySpecifiedColors = stream.getExplicitlySpecifiedColorsCopy();
			explicitlySpecifiedColors.forEachEntry((k, v) -> {
				map.put(k, Colors.toColor(new ARGBType(v)));
				return true;
			});
			LOG.debug("internal map={} updated map={}", userSpecifiedColors, map);
			if (!userSpecifiedColors.equals(map))
			{
				userSpecifiedColors.clear();
				userSpecifiedColors.putAll(map);
			}
		}
	}

	@Override
	public ObservableMap<Long, Color> userSpecifiedColors()
	{
		return this.unmodifiableSpecifiedColors;
	}

	@Override
	public void setColor(final long id, final Color color)
	{
		stream.specifyColorExplicitly(id, Colors.toARGBType(color).get());
	}

	@Override
	public void removeColor(final long id)
	{
		stream.removeExplicitColor(id);
	}

}
