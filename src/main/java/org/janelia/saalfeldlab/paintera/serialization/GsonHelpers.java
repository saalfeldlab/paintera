package org.janelia.saalfeldlab.paintera.serialization;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSourceSerializer;
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSourceSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.InvertingRawSourceState;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;

import com.google.gson.GsonBuilder;

import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.realtransform.AffineTransform3D;

public class GsonHelpers
{

	public static GsonBuilder builderWithAllRequiredAdapters(
			final PainteraBaseView viewer,
			final Supplier< String > projectDirectory
			)
	{
		final IntFunction< SourceState< ?, ? > > dependencyFromIndex = index -> viewer.sourceInfo().getState( viewer.sourceInfo().trackSources().get( index ) );
		return builderWithAllRequiredAdapters( new Arguments( viewer ), projectDirectory, dependencyFromIndex );
	}

	public static GsonBuilder builderWithAllRequiredAdapters(
			final Arguments arguments,
			final Supplier< String > projectDirectory,
			final IntFunction< SourceState< ?, ? > > dependencyFromIndex
			)
	{
		return new GsonBuilder()
				.registerTypeAdapter( SourceInfo.class, new SourceInfoSerializer() )
				.registerTypeAdapter( SourceStateWithIndexedDependencies.class, new SourceStateSerializer() )
				.registerTypeAdapter( N5DataSource.class, new N5DataSourceSerializer( arguments.sharedQueue, 0 ) )
				.registerTypeAdapter( MaskedSource.class, new MaskedSourceSerializer( projectDirectory, arguments.propagationWorkers ) )
				.registerTypeAdapter( RawSourceState.class, new RawSourceStateSerializer() )
				.registerTypeAdapter( InvertingRawSourceState.class, new InvertingSourceStateSerializer( dependencyFromIndex ) )
				.registerTypeAdapter( LabelSourceState.class, new LabelSourceStateSerializer<>( arguments ) )
				.registerTypeHierarchyAdapter( ARGBColorConverter.class, new ARGBColorConverterSerializer<>() )
				.registerTypeHierarchyAdapter( HighlightingStreamConverter.class, new HighlightingStreamConverterSerializer() )
				.registerTypeAdapter( AffineTransform3D.class, new AffineTransform3DJsonAdapter() )
				.registerTypeHierarchyAdapter( Composite.class, new CompositeSerializer() )
				.registerTypeAdapter( SelectedIds.class, new SelectedIdsSerializer() )
				.registerTypeAdapter( FragmentSegmentAssignmentOnlyLocal.class, new FragmentSegmentAssignmentOnlyLocalSerializer() )
				.registerTypeAdapter( CommitCanvasN5.class, new CommitCanvasN5Serializer() )
				.registerTypeAdapter( WindowProperties.class, new WindowPropertiesSerializer() );
	}

	private static List< ? extends StatefulSerializer.SerializerAndDeserializer< ?, ? > > findFactories( final String... files )
	{
		return new ArrayList<>();
//		final ClassFinder finder = new ClassFinder();
//		finder.addClassPath();
//		finder.add( Arrays.stream( files ).map( File::new ).toArray( File[]::new) );
//		final ClassFilter filter = new AndClassFilter(
//				new NotClassFilter( new InterfaceOnlyClassFilter() ),
//				new SubclassClassFilter( StatefulSerializer.SerializerAndDeserializer.class ),
//				new NotClassFilter( new AbstractClassFilter() ) );
//
//		final List< ClassInfo > classes = new ArrayList<>();
//		finder.findClasses( classes, filter );
//
//		return classes
//				.stream()
//				.map( clazz -> clazz. )

	}

}
