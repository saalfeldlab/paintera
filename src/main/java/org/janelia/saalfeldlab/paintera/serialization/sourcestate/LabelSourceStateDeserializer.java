package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMaskGenerators;
import org.janelia.saalfeldlab.paintera.serialization.FragmentSegmentAssignmentOnlyLocalSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import net.imglib2.Interval;
import net.imglib2.type.numeric.ARGBType;

public class LabelSourceStateDeserializer< C extends HighlightingStreamConverter< ? > >
extends SourceStateSerialization.SourceStateDeserializerWithoutDependencies< LabelSourceState< ?, ? >, C >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static final String SELECTED_IDS_KEY = "selectedIds";

	public static final String ASSIGNMENT_KEY = "assignment";

	public static final String FRAGMENTS_KEY = "fragments";

	public static final String SEGMENTS_KEY = "segments";

	private final Arguments arguments;

	public LabelSourceStateDeserializer( final Arguments arguments )
	{
		super();
		this.arguments = arguments;
	}

	public static class Factory< C extends HighlightingStreamConverter< ? > > implements StatefulSerializer.Deserializer< LabelSourceState< ?, ? >, LabelSourceStateDeserializer< C > >
	{

		@Override
		public LabelSourceStateDeserializer< C > createDeserializer( final Arguments arguments, final Supplier< String > projectDirectory, final IntFunction< SourceState< ?, ? > > dependencyFromIndex )
		{
			return new LabelSourceStateDeserializer<>( arguments );
		}

	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	@Override
	protected LabelSourceState< ?, ? > makeState(
			final JsonObject map,
			final DataSource< ?, ? > source,
			final Composite< ARGBType, ARGBType > composite,
			final C converter,
			final String name,
			final SourceState< ?, ? >[] dependsOn,
			final JsonDeserializationContext context ) throws IOException
	{
		final boolean isMaskedSource = source instanceof MaskedSource< ?, ? >;
		LOG.debug( "Is {} masked source? {}", source, isMaskedSource );
		if ( isMaskedSource )
		{
			LOG.debug( "Underlying source: {}", ( ( MaskedSource< ?, ? > ) source ).underlyingSource() );
		}

		if ( isMaskedSource && !( ( ( MaskedSource< ?, ? > ) source ).underlyingSource() instanceof N5DataSource< ?, ? > ) )
		{
			LOG.error( "Underlying source is not n5! Returning null pointer!" );
			return null;
		}

		if ( !isMaskedSource && !( source instanceof N5DataSource< ?, ? > ) )
		{
			LOG.error( "Source is not n5! Returning null pointer!" );
			return null;
		}

		final N5DataSource< ?, ? > n5Source = ( N5DataSource< ?, ? > ) ( isMaskedSource
				? ( ( MaskedSource< ?, ? > ) source ).underlyingSource()
						: source );

		final N5Writer writer = n5Source.writer();
		final String dataset = n5Source.dataset();

		final SelectedIds selectedIds = context.deserialize( map.get( SELECTED_IDS_KEY ), SelectedIds.class );
		final JsonObject assignmentMap = map.get( ASSIGNMENT_KEY ).getAsJsonObject();
		final FragmentSegmentAssignmentState assignment = N5Helpers.assignments(
				writer,
				dataset,
				context.deserialize( assignmentMap.get( FragmentSegmentAssignmentOnlyLocalSerializer.FRAGMENTS_KEY ), long[].class ),
				context.deserialize( assignmentMap.get( FragmentSegmentAssignmentOnlyLocalSerializer.FRAGMENTS_KEY ), long[].class ) );

		final SelectedSegments selectedSegments = new SelectedSegments( selectedIds, assignment );
		final FragmentsInSelectedSegments fragmentsInSelectedSegments = new FragmentsInSelectedSegments( selectedSegments, assignment );

		final AbstractHighlightingARGBStream stream = converter.getStream();
		stream.setHighlightsAndAssignment( selectedIds, assignment );
		final LongFunction< ? > maskGenerator = PainteraBaseView.equalsMaskForType( source.getDataType() );
		final Function< TLongHashSet, ? > segmentMaskGenerator = SegmentMaskGenerators.forType( source.getDataType() );

		final InterruptibleFunction< Long, Interval[] >[] blockCaches = PainteraBaseView.generateLabelBlocksForLabelCache( n5Source, PainteraBaseView.scaleFactorsFromAffineTransforms( source ) );
		final InterruptibleFunction[] meshCache = CacheUtils.segmentMeshCacheLoaders(
				( DataSource ) source,
				(Function) segmentMaskGenerator,
				CacheUtils::toCacheSoftRefLoaderCache );
		LOG.debug( "Meshses group: {}", arguments.meshesGroup );
		final MeshManagerWithAssignmentForSegments meshManager = new MeshManagerWithAssignmentForSegments(
				source,
				blockCaches,
				meshCache,
				arguments.meshesGroup,
				assignment,
				selectedSegments,
				stream,
				new SimpleIntegerProperty(),
				new SimpleDoubleProperty(),
				new SimpleIntegerProperty(),
				arguments.meshManagerExecutors,
				arguments.meshWorkersExecutors );
		final MeshInfos meshInfos = new MeshInfos( selectedSegments, assignment, meshManager, source.getNumMipmapLevels() );

		return new LabelSourceState(
				source,
				converter,
				composite,
				name,
				maskGenerator,
				segmentMaskGenerator,
				assignment,
				ToIdConverter.fromType( source.getDataType() ),
				selectedIds,
				N5Helpers.idService( writer, dataset ),
				meshManager,
				meshInfos );

	}

}
