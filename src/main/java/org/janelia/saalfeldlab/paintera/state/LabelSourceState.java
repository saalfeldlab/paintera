package org.janelia.saalfeldlab.paintera.state;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.LongFunction;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsState;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunctionAndCache;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.cache.BlocksForLabelDelegate;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMaskGenerators;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;

import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Group;
import net.imglib2.Interval;
import net.imglib2.cache.UncheckedCache;
import net.imglib2.converter.Converter;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;

public class LabelSourceState< D, T >
		extends
		MinimalSourceState< D, T, DataSource< D, T >, HighlightingStreamConverter< T > >
		implements
		HasMeshes< TLongHashSet >,
		HasMeshCache< TLongHashSet >
{

	private final LongFunction< Converter< D, BoolType > > maskForLabel;

	private final Function< TLongHashSet, Converter< D, BoolType > > segmentMaskGenerator;

	private final FragmentSegmentAssignmentState assignment;

	private final ToIdConverter toIdConverter;

	private final SelectedIds selectedIds;

	private final IdService idService;

	private final MeshManager< TLongHashSet > meshManager;

	private final MeshInfos< TLongHashSet > meshInfos;

	private final InterruptibleFunctionAndCache< ShapeKey< TLongHashSet >, Pair< float[], float[] > >[] meshCaches;

	private final LockedSegmentsState lockedSegments;

	public LabelSourceState(
			final DataSource< D, T > dataSource,
			final HighlightingStreamConverter< T > converter,
			final Composite< ARGBType, ARGBType > composite,
			final String name,
			final FragmentSegmentAssignmentState assignment,
			final LockedSegmentsState lockedSegments,
			final IdService idService,
			final SelectedIds selectedIds,
			final Group meshesGroup,
			final ExecutorService meshManagerExecutors,
			final ExecutorService meshWorkersExecutors )
	{
		super( dataSource, converter, composite, name );
		final D d = dataSource.getDataType();
		this.maskForLabel = PainteraBaseView.equalsMaskForType( d );
		this.segmentMaskGenerator = SegmentMaskGenerators.forType( d );
		this.assignment = assignment;
		this.lockedSegments = lockedSegments;
		this.toIdConverter = ToIdConverter.fromType( d );
		this.selectedIds = selectedIds;
		this.idService = idService;

		final SelectedSegments selectedSegments = new SelectedSegments( selectedIds, assignment );

		final InterruptibleFunction< Long, Interval[] >[] blockCaches = PainteraBaseView.generateLabelBlocksForLabelCache( dataSource );

		final BlocksForLabelDelegate< TLongHashSet, Long >[] delegateBlockCaches = BlocksForLabelDelegate.delegate( blockCaches, ids -> Arrays.stream( ids.toArray() ).mapToObj( id -> id ).toArray( Long[]::new ), meshWorkersExecutors );

		this.meshCaches = CacheUtils.segmentMeshCacheLoaders(
				dataSource,
				segmentMaskGenerator,
				CacheUtils::toCacheSoftRefLoaderCache );
		final MeshManagerWithAssignmentForSegments meshManager = new MeshManagerWithAssignmentForSegments(
				dataSource,
				delegateBlockCaches,
				meshCaches,
				meshesGroup,
				assignment,
				selectedSegments,
				converter.getStream(),
				new SimpleIntegerProperty(),
				new SimpleDoubleProperty(),
				new SimpleIntegerProperty(),
				meshManagerExecutors,
				meshWorkersExecutors );
		final MeshInfos< TLongHashSet > meshInfos = new MeshInfos<>( selectedSegments, assignment, meshManager, assignment::getFragments, dataSource.getNumMipmapLevels() );

		this.meshManager = meshManager;
		this.meshInfos = meshInfos;

		assignment.addListener( obs -> stain() );
		selectedIds.addListener( obs -> stain() );
		lockedSegments.addListener( obs -> stain() );
	}

	public ToIdConverter toIdConverter()
	{
		return this.toIdConverter;
	}

	public LongFunction< Converter< D, BoolType > > maskForLabel()
	{
		return this.maskForLabel;
	}

	@Override
	public MeshManager< TLongHashSet > meshManager()
	{
		return this.meshManager;
	}

	@Override
	public MeshInfos< TLongHashSet > meshInfos()
	{
		return this.meshInfos;
	}

	public FragmentSegmentAssignmentState assignment()
	{
		return this.assignment;
	}

	public IdService idService()
	{
		return this.idService;
	}

	public SelectedIds selectedIds()
	{
		return this.selectedIds;
	}

	@Override
	public void invalidateAll()
	{
		Arrays
				.stream( this.meshCaches )
				.forEach( UncheckedCache::invalidateAll );
	}

	public LockedSegmentsState lockedSegments()
	{
		return this.lockedSegments;
	}

}
