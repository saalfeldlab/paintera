package org.janelia.saalfeldlab.paintera.state;

import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX;
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerState;
import bdv.viewer.Interpolation;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealViews;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.data.DataSource;

import java.util.function.Consumer;

public class VisitEveryDisplayPixel {

	public static <D> void visitEveryDisplayPixel(
			final DataSource<D, ?> dataSource,
			final ViewerPanelFX viewer,
			final Consumer<D> doAtPixel) {

		final ViewerState viewerState = viewer.getState().copy();

		final AffineTransform3D viewerTransform = new AffineTransform3D();
		viewerState.getViewerTransform(viewerTransform);

		final AffineTransform3D screenScaleTransform = new AffineTransform3D();
		viewer.getRenderUnit().getScreenScaleTransform(0, screenScaleTransform);
		final int level = viewerState.getBestMipMapLevel(screenScaleTransform, dataSource);

		final AffineTransform3D sourceTransform = new AffineTransform3D();
		dataSource.getSourceTransform(0, level, sourceTransform);

		final RealRandomAccessible<D> interpolatedSource = dataSource.getInterpolatedDataSource(0, level, Interpolation.NEARESTNEIGHBOR);

		final RealTransformRealRandomAccessible<D, InverseRealTransform> transformedSource = RealViews.transformReal(
				interpolatedSource,
				sourceTransform);

		final int w = (int)viewer.getWidth();
		final int h = (int)viewer.getHeight();
		final IntervalView<D> dataOnScreen =
				Views.interval(
						Views.hyperSlice(
								RealViews.affine(transformedSource, viewerTransform), 2, 0),
						new FinalInterval(w, h));

		final Cursor<D> cursor = Views.flatIterable(dataOnScreen).cursor();
		while (cursor.hasNext()) {
			doAtPixel.accept(cursor.next());
		}
	}
}
