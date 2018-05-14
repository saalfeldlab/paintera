package org.janelia.saalfeldlab.paintera.serialization;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;

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
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignment;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import net.imglib2.Interval;
import net.imglib2.type.numeric.ARGBType;

public class LabelSourceStateSerializer< C extends HighlightingStreamConverter< ? > > extends AbstractSourceStateSerializer< LabelSourceState< ?, ? >, C >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static final String SELECTED_IDS_KEY = "selectedIds";

	public static final String ASSIGNMENT_KEY = "assignment";

	public static final String FRAGMENTS_KEY = "fragments";

	public static final String SEGMENTS_KEY = "segments";

	private final Arguments arguments;

	public LabelSourceStateSerializer( final Arguments arguments )
	{
		super();
		this.arguments = arguments;
	}

	@Override
	public JsonObject serialize( final LabelSourceState< ?, ? > state, final Type type, final JsonSerializationContext context )
	{
		final JsonObject map = super.serialize( state, type, context );
		map.add( SELECTED_IDS_KEY, context.serialize( state.selectedIds(), state.selectedIds().getClass() ) );
		map.add( ASSIGNMENT_KEY, context.serialize( state.assignment() ) );
		return map;
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	@Override
	protected LabelSourceState< ?, ? > makeState(
			final JsonObject map,
			final DataSource< ?, ? > source,
			final Composite< ARGBType, ARGBType > composite,
			final String name,
			final SourceState< ?, ? >[] dependsOn,
			final JsonDeserializationContext context ) throws IOException
	{
		final boolean isMaskedSource = source instanceof MaskedSource< ?, ? >;
		LOG.warn( "Is {} masked source? {}", source, isMaskedSource );
		if ( isMaskedSource )
		{
			LOG.warn( "Underlying source: {}", ((MaskedSource<?,?>)source).underlyingSource() );
		}

		if ( isMaskedSource && !(((MaskedSource<?,?>)source).underlyingSource() instanceof N5DataSource< ?, ? >))
		{
			LOG.warn( "Returning null pointer!" );
			return null;
		}

		if ( !isMaskedSource && !(source instanceof N5DataSource< ?, ? >)) {
			return null;
		}

		final N5DataSource< ?, ? > n5Source = (N5DataSource< ?, ? >) ( isMaskedSource
				? ((MaskedSource<?,?>)source).underlyingSource()
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

		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selectedIds, assignment );

		final InterruptibleFunction< Long, Interval[] >[] blockCaches = PainteraBaseView.generateLabelBlocksForLabelCache( n5Source, PainteraBaseView.scaleFactorsFromAffineTransforms( source ) );
		final InterruptibleFunction[] meshCache = CacheUtils.meshCacheLoaders( (DataSource)source, PainteraBaseView.equalsMaskForType( source.getType() ), CacheUtils::toCacheSoftRefLoaderCache );
		final MeshManagerWithAssignment meshManager = new MeshManagerWithAssignment(
				source,
				blockCaches,
				meshCache,
				arguments.meshesGroup,
				assignment,
				fragmentsInSelectedSegments,
				stream,
				new SimpleIntegerProperty(),
				new SimpleDoubleProperty(),
				new SimpleIntegerProperty(),
				arguments.meshManagerExecutors,
				arguments.meshWorkersExecutors);
		final MeshInfos meshInfos = new MeshInfos( selectedSegments, assignment, meshManager, source.getNumMipmapLevels() );

		return new LabelSourceState(
				source,
				HighlightingStreamConverter.forType( stream, source.getType() ),
				composite,
				name,
				PainteraBaseView.equalsMaskForType( source.getType() ),
				assignment,
				ToIdConverter.fromType( source.getDataType() ),
				selectedIds,
				N5Helpers.idService( writer, dataset ),
				meshManager,
				meshInfos );

	}

}
