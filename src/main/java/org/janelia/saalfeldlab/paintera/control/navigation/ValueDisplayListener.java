package org.janelia.saalfeldlab.paintera.control.navigation;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.ui.TransformListener;
import net.imglib2.view.composite.Composite;
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource;
import org.janelia.saalfeldlab.paintera.data.DataSource;

public class ValueDisplayListener
		implements EventHandler<javafx.scene.input.MouseEvent>, TransformListener<AffineTransform3D>
{

	private final ViewerPanelFX viewer;

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	private final ObservableValue<Source<?>> currentSource;

	private double x = -1;

	private double y = -1;

	private final Function<Source<?>, Interpolation> interpolation;

	private final Consumer<String> submitValue;

	public ValueDisplayListener(
			final ViewerPanelFX viewer,
			final ObservableValue<Source<?>> currentSource,
			final Function<Source<?>, Interpolation> interpolation,
			final Consumer<String> submitValue)
	{
		super();
		this.viewer = viewer;
		this.currentSource = currentSource;
		this.interpolation = interpolation;
		this.submitValue = submitValue;
	}

	@Override
	public void handle(final MouseEvent e)
	{
		x = e.getX();
		y = e.getY();

		synchronized (viewer)
		{
			getInfo();
		}
	}

	@Override
	public void transformChanged(final AffineTransform3D transform)
	{
		this.viewerTransform.set(transform);
		synchronized (viewer)
		{
			getInfo();
		}
	}

	private static <D> D getVal(final double x, final double y, final RealRandomAccess<D> access, final ViewerPanelFX
			viewer)
	{
		access.setPosition(x, 0);
		access.setPosition(y, 1);
		access.setPosition(0l, 2);
		return getVal(access, viewer);
	}

	private static <D> D getVal(final RealRandomAccess<D> access, final ViewerPanelFX viewer)
	{
		viewer.displayToGlobalCoordinates(access);
		return access.get();
	}

	private <D> void getInfo()
	{
		final Optional<Source<?>> optionalSource = Optional.ofNullable(currentSource.getValue());
		if (optionalSource.isPresent() && optionalSource.get() instanceof DataSource<?, ?>)
		{
			@SuppressWarnings("unchecked") final DataSource<D, ?> source = (DataSource<D, ?>) optionalSource.get();
			final ViewerState       state                = viewer.getState();
			final Interpolation     interpolation        = this.interpolation.apply(source);
			final AffineTransform3D screenScaleTransform = new AffineTransform3D();
			viewer.getRenderUnit().getScreenScaleTransform(0, screenScaleTransform);
			final int               level                = state.getBestMipMapLevel(screenScaleTransform, source);
			final AffineTransform3D affine               = new AffineTransform3D();
			source.getSourceTransform(0, level, affine);
			final RealRandomAccess<D> access = RealViews.transformReal(
					source.getInterpolatedDataSource(
							0,
							level,
							interpolation
					                                ),
					affine
			                                                          ).realRandomAccess();
			final D                   val    = getVal(x, y, access, viewer);
			submitValue.accept(stringConverterFromSource(source).apply(val));
		}
	}

	private static<D> Function<D, String> stringConverterFromSource(final DataSource<D, ? > source)
	{
		if (source instanceof ChannelDataSource<?, ?>)
		{
			final long numChannels = ((ChannelDataSource<?, ?>)source).numChannels();
			return (Function) (Function<? extends Composite<?>, String>) comp -> {
				StringBuilder sb = new StringBuilder("(");
				if (numChannels > 0)
					sb.append(comp.get(0).toString());

				for (int channel = 1; channel < numChannels; ++channel)
					sb.append((", ")).append(comp.get(channel).toString());

				sb.append((")"));
				return sb.toString();
			};
		}
		return stringConverter(source.getDataType());
	}

	private static <D> Function<D, String> stringConverter(final D d)
	{
		// TODO are we ever going to need anything other than toString?
		return D::toString;
	}

}
