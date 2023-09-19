package org.janelia.saalfeldlab.paintera.control.navigation;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.TransformListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.view.composite.Composite;
import org.janelia.saalfeldlab.fx.Tasks;
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.util.NamedThreadFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

public class ValueDisplayListener
		implements EventHandler<javafx.scene.input.MouseEvent>, TransformListener<AffineTransform3D> {

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
			final Consumer<String> submitValue) {

		super();
		this.viewer = viewer;
		this.currentSource = currentSource;
		this.interpolation = interpolation;
		this.submitValue = submitValue;
	}

	@Override
	public void handle(final MouseEvent e) {

		x = e.getX();
		y = e.getY();

		synchronized (viewer) {
			getInfo();
		}
	}

	@Override
	public void transformChanged(final AffineTransform3D transform) {

		/* check if the transforms are different or not */
		final var isChanged = !Arrays.equals(transform.getRowPackedCopy(), this.viewerTransform.getRowPackedCopy());
		if (isChanged) {
			this.viewerTransform.set(transform);
			synchronized (viewer) {
				getInfo();
			}
		}
	}

	private static <D> D getVal(final double x, final double y, final RealRandomAccess<D> access, final ViewerPanelFX viewer) {

		access.setPosition(x, 0);
		access.setPosition(y, 1);
		access.setPosition(0L, 2);
		return getVal(access, viewer);
	}

	private static <D> D getVal(final RealRandomAccess<D> access, final ViewerPanelFX viewer) {

		viewer.displayToGlobalCoordinates(access);
		return access.get();
	}

	private final Map<DataSource<?, ?>, Task<?>> taskMap = new HashMap<>();

	private final ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("value-display-listener", true, 2));

	private <D> void getInfo() {

		final Optional<Source<?>> optionalSource = Optional.ofNullable(currentSource.getValue());
		if (optionalSource.isPresent() && optionalSource.get() instanceof DataSource<?, ?>) {
			final DataSource<D, ?> source = (DataSource<D, ?>) optionalSource.get();

			final var taskObj = Tasks.<String>createTask(t -> {
				final ViewerState state = viewer.getState();
				final Interpolation interpolation = this.interpolation.apply(source);
				final AffineTransform3D screenScaleTransform = new AffineTransform3D();
				viewer.getRenderUnit().getScreenScaleTransform(0, screenScaleTransform);
				final int level = state.getBestMipMapLevel(screenScaleTransform, source);
				final AffineTransform3D affine = new AffineTransform3D();
				source.getSourceTransform(0, level, affine);
				final RealRandomAccess<D> access = RealViews.transformReal(
						source.getInterpolatedDataSource(
								0,
								level,
								interpolation
						),
						affine
				).realRandomAccess();
				final var val = getVal(x, y, access, viewer);
				return stringConverterFromSource(source).apply(val);
			}).onSuccess((event, t) -> {
				/* submit the value if the task is completed; remove from the map*/
				submitValue.accept(t.getValue());
				taskMap.remove(source);
			});

			/* If we are creating a task for a source which has a running task, cancel the old task after removing. */
			Optional.ofNullable(taskMap.put(source, taskObj)).ifPresent(Task::cancel);

			taskObj.submit(executor);

		}
	}

	private static <D> Function<D, String> stringConverterFromSource(final DataSource<D, ?> source) {

		if (source instanceof ChannelDataSource<?, ?>) {
			final long numChannels = ((ChannelDataSource<?, ?>) source).numChannels();

			// Cast not actually redundant
			//noinspection unchecked,RedundantCast
			return (Function<D, String>) (Function<? extends Composite<?>, String>) comp -> {
				StringBuilder sb = new StringBuilder("(");
				if (numChannels > 0)
					sb.append(comp.get(0).toString());

				for (int channel = 1; channel < numChannels; ++channel) {
					sb.append((", ")).append(comp.get(channel).toString());
				}

				sb.append((")"));
				return sb.toString();
			};
		}
		return stringConverter(source.getDataType());
	}

	private static <D> Function<D, String> stringConverter(final D d) {
		// TODO are we ever going to need anything other than toString?
		return D::toString;
	}

}
