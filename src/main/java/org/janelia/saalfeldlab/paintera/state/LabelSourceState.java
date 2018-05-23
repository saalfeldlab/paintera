package org.janelia.saalfeldlab.paintera.state;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongFunction;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.meshes.cache.IntervalFileIO;
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMaskGenerators;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;

import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Group;
import net.imglib2.Interval;
import net.imglib2.converter.Converter;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;
import tmp.net.imglib2.cache.GeneralDiskCache;

public class LabelSourceState< D, T > extends MinimalSourceState< D, T, DataSource< D, T >, HighlightingStreamConverter< T > >
{

	private final LongFunction< Converter< D, BoolType > > maskForLabel;

	private final Function< TLongHashSet, Converter< D, BoolType > > segmentMaskGenerator;

	private final FragmentSegmentAssignmentState assignment;

	private final ToIdConverter toIdConverter;

	private final SelectedIds selectedIds;

	private final IdService idService;

	private final MeshManager< TLongHashSet > meshManager;

	private final MeshInfos meshInfos;

	public LabelSourceState(
			final DataSource< D, T > dataSource,
			final HighlightingStreamConverter< T > converter,
			final Composite< ARGBType, ARGBType > composite,
			final String name,
			final FragmentSegmentAssignmentState assignment,
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
		this.toIdConverter = ToIdConverter.fromType( d );
		this.selectedIds = selectedIds;
		this.idService = idService;



		final SelectedSegments selectedSegments = new SelectedSegments( selectedIds, assignment );

		Path cacheLocation;
		try {
			cacheLocation = GeneralDiskCache.createTempDirectory( null, true );
		}
		catch ( final IOException e )
		{
			throw new RuntimeException( e );
		}


		final BiFunction< Path, Long, Path > filePathFromKey = ( path, l ) -> path.resolve( l.toString() );

		final IntervalFileIO fileIO = new IntervalFileIO();

		final InterruptibleFunction< Long, Interval[] >[] blockCaches = PainteraBaseView.generateLabelBlocksForLabelCache(
				dataSource,
				loader -> CacheUtils.toDiskCacheBackedSoftRedLoaderCache( loader, cacheLocation, filePathFromKey, fileIO ) );
		final InterruptibleFunction< ShapeKey< TLongHashSet >, Pair< float[], float[] > >[] meshCache = CacheUtils.segmentMeshCacheLoaders(
				dataSource,
				segmentMaskGenerator,
				CacheUtils::toCacheSoftRefLoaderCache );
		final MeshManagerWithAssignmentForSegments meshManager = new MeshManagerWithAssignmentForSegments(
				dataSource,
				blockCaches,
				meshCache,
				meshesGroup,
				assignment,
				selectedSegments,
				converter.getStream(),
				new SimpleIntegerProperty(),
				new SimpleDoubleProperty(),
				new SimpleIntegerProperty(),
				meshManagerExecutors,
				meshWorkersExecutors );
		final MeshInfos< TLongHashSet > meshInfos = new MeshInfos<>( selectedSegments, assignment, meshManager, dataSource.getNumMipmapLevels() );

		this.meshManager = meshManager;
		this.meshInfos = meshInfos;

		assignment.addListener( obs -> stain() );
		selectedIds.addListener( obs -> stain() );
	}

	public ToIdConverter toIdConverter()
	{
		return this.toIdConverter;
	}

	public LongFunction< Converter< D, BoolType > > maskForLabel()
	{
		return this.maskForLabel;
	}

	public MeshManager< TLongHashSet > meshManager()
	{
		return this.meshManager;
	}

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

}
