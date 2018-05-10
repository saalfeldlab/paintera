package org.janelia.saalfeldlab.paintera.data.meta;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.meta.exception.SourceCreationFailed;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import javafx.scene.Group;
import net.imglib2.RandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;

public interface LabelMeta< D, T > extends Meta
{

	public LabelSourceState< D, T > asSource(
			final Optional< Pair< long[], long[] > > initialAssignment,
			final SelectedIds selectedIds,
			final Composite< ARGBType, ARGBType > composite,
			final SharedQueue sharedQueue,
			final int priority,
			final Function< Interpolation, InterpolatorFactory< D, RandomAccessible< D > > > dataInterpolation,
			final Function< Interpolation, InterpolatorFactory< T, RandomAccessible< T > > > viewerInterpolation,
			final AffineTransform3D transform,
			final String name,
			final String canvasDir,
			final Supplier< String > canvasCacheDirUpdate,
			final ExecutorService propagationExecutor,
			final ExecutorService manager,
			final ExecutorService workers,
			final Group meshesGroup,
			SourceState< ?, ? >... dependson ) throws SourceCreationFailed;

}
