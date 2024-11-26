package org.janelia.saalfeldlab.paintera.control.navigation;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.TransformListener;
import io.github.oshai.kotlinlogging.KLogger;
import io.github.oshai.kotlinlogging.KotlinLogging;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import kotlin.Unit;
import kotlinx.coroutines.Deferred;
import net.imglib2.RealRandomAccess;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.view.composite.Composite;
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX;
import org.janelia.saalfeldlab.fx.Tasks;
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource;
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class ValueDisplayListener<T> implements EventHandler<MouseEvent>, TransformListener<AffineTransform3D> {

	private final ViewerPanelFX viewer;

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	private final SimpleBooleanProperty viewerTransformChanged = new SimpleBooleanProperty();
	private final ObservableValue<Source<T>> source;
	private final ObservableValue<RealRandomAccess<T>> accessBinding;

	private double x = -1;
	private double y = -1;

	private final Consumer<String> submitValue;

	public ValueDisplayListener(
			final ViewerPanelFX viewer,
			final ObservableValue<Source<?>> currentSource,
			final Function<Source<?>, Interpolation> interpolation,
			final Consumer<String> submitValue) {

		this.viewer = viewer;
		this.source = currentSource.map(it -> (Source<T>)it);
		this.submitValue = submitValue;
		this.accessBinding = Bindings.createObjectBinding(() -> {
					final var source = this.source.getValue();
					if (source == null)
						return null;
					final int level = viewer.getState().getBestMipMapLevel(source);
					final var interp = interpolation.apply(source);
					final var affine = new AffineTransform3D();
					source.getSourceTransform(0, level, affine);
					return RealViews.transformReal(
							source.getInterpolatedSource(
									0,
									level,
									interp
							),
							affine
					).realRandomAccess();
				}, currentSource, viewer.getRenderUnit().getScreenScalesProperty(), viewerTransformChanged, viewer.getRenderUnit().getRepaintRequestProperty()
		);

		this.accessBinding.addListener((obs, old, newAccess) -> {
			if (newAccess == null)
				return;
			synchronized (viewer) {
				getInfo();
			}
		});
	}

	@Override
	public void handle(final MouseEvent e) {

		if (x == e.getX() && y == e.getY())
			return;

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
		}
	}

	private T getVal() {

		final var access = accessBinding.getValue();
		access.setPosition(x, 0);
		access.setPosition(y, 1);
		access.setPosition(0L, 2);
		viewer.displayToGlobalCoordinates(access);
		return access.get();
	}

	private final Map<Source<T>, Deferred<?>> taskMap = new HashMap<>();

	private void getInfo() {

		final Source<T> source = this.source.getValue();
		if (source == null) return;

		final var job = Tasks.createTask(() -> stringConverterFromSource(source).apply(getVal()))
				.onSuccess(result -> Platform.runLater(() -> submitValue.accept(result)))
				.onEnd((result, cause) -> {

							if (cause != null)
								LOG.error(cause, () -> Unit.INSTANCE);

							taskMap.remove(source);
						}
				);


		/* If we are creating a task for a source which has a running task, cancel and remove the old task. */
		taskMap.computeIfPresent(source, (key, prevJob) -> {
			prevJob.cancel(null);
			return job;
		});

		job.start();
	}

	private static KLogger LOG = KotlinLogging.INSTANCE.logger(() -> Unit.INSTANCE);

	private static <T> Function<T, String> stringConverterFromSource(@Nonnull final Source<T> source) {

		if (source instanceof ChannelDataSource<?, ?>) {
			final long numChannels = ((ChannelDataSource<?, ?>)source).numChannels();

			// Cast not actually redundant
			//noinspection unchecked,RedundantCast
			return (Function<T, String>)(Function<VolatileWithSet<? extends Composite<?>>, String>)volWithSet -> {
				final var comp = volWithSet.get();
				final Function<Object, String> stringConverter = stringConverter(comp.get(0));
				StringBuilder sb = new StringBuilder("(");
				if (numChannels > 0)
					sb.append(stringConverter.apply(comp.get(0)));

				for (int channel = 1; channel < numChannels; ++channel) {
					sb.append((", ")).append(stringConverter.apply(comp.get(channel)));
				}

				sb.append((")"));
				return sb.toString();
			};
		}
		return stringConverter(source.getType());
	}

	private static <T> Function<T, String> stringConverter(final T t) {
		if (t instanceof Volatile<?>)
			return it -> ((Volatile<?>)it).get().toString();
		return T::toString;
	}

}
