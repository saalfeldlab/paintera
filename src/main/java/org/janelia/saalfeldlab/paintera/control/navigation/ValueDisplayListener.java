package org.janelia.saalfeldlab.paintera.control.navigation;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.TransformListener;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import kotlinx.coroutines.Deferred;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.view.composite.Composite;
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX;
import org.janelia.saalfeldlab.fx.Tasks;
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource;
import org.janelia.saalfeldlab.paintera.data.DataSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class ValueDisplayListener<D> implements EventHandler<MouseEvent>, TransformListener<AffineTransform3D> {

	private final ViewerPanelFX viewer;

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	private final SimpleBooleanProperty viewerTransformChanged = new SimpleBooleanProperty();
	private final ObservableValue<? extends DataSource<D, ?>> dataSource;
	private final ObservableValue<RealRandomAccess<D>> accessBinding;

	private double x = -1;
	private double y = -1;

	private final Consumer<String> submitValue;

	public ValueDisplayListener(
			final ViewerPanelFX viewer,
			final ObservableValue<Source<?>> currentSource,
			final Function<Source<?>, Interpolation> interpolation,
			final Consumer<String> submitValue) {

		this.viewer = viewer;
		this.dataSource = currentSource.map(it -> (DataSource<D, ?>)it).when(currentSource.map(it -> it instanceof DataSource<?, ?>));
		this.submitValue = submitValue;
		this.accessBinding = Bindings.createObjectBinding(() -> {
					final var source = this.dataSource.getValue();
					final int level = viewer.getState().getBestMipMapLevel(source);
					final var interp = interpolation.apply(source);
					final var affine = new AffineTransform3D();
					source.getSourceTransform(0, level, affine);
					return RealViews.transformReal(
							source.getInterpolatedDataSource(
									0,
									level,
									interp
							),
							affine
					).realRandomAccess();
				}, currentSource, viewer.getRenderUnit().getScreenScalesProperty(), viewerTransformChanged
		);
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
		final var isChanged = !Arrays.equals(transform.getRowPackedCopy(), viewerTransform.getRowPackedCopy());
		if (isChanged) {
			viewerTransform.set(transform);
			viewerTransformChanged.setValue(!viewerTransformChanged.getValue());
			synchronized (viewer) {
				getInfo();
			}
		}
	}

	private D getVal() {

		final var access = accessBinding.getValue();
		access.setPosition(x, 0);
		access.setPosition(y, 1);
		access.setPosition(0L, 2);
		viewer.displayToGlobalCoordinates(access);
		return access.get();
	}

	private final Map<DataSource<D, ?>, Deferred<?>> taskMap = new HashMap<>();

	private void getInfo() {

		final DataSource<D, ?> source = dataSource.getValue();

		final var job = Tasks.createTask(() -> stringConverterFromSource(source).apply(getVal()))
				.onSuccess(result -> Platform.runLater(() -> submitValue.accept(result)))
				.onEnd((result, cause) -> taskMap.remove(source));


		/* If we are creating a task for a source which has a running task, cancel and remove the old task. */
		taskMap.computeIfPresent(source, (key, prevJob) -> {
			prevJob.cancel(null);
			return job;
		});

		job.start();
	}

	private static <D> Function<D, String> stringConverterFromSource(final DataSource<D, ?> source) {

		if (source instanceof ChannelDataSource<?, ?>) {
			final long numChannels = ((ChannelDataSource<?, ?>)source).numChannels();

			// Cast not actually redundant
			//noinspection unchecked,RedundantCast
			return (Function<D, String>)(Function<? extends Composite<?>, String>)comp -> {
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
