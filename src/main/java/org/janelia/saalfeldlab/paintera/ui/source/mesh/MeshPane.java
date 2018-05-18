package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.set.hash.TLongHashSet;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import net.imglib2.Interval;
import net.imglib2.util.Pair;

public class MeshPane implements BindUnbindAndNodeSupplier, ListChangeListener< MeshInfo< TLongHashSet > >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final MeshManager< TLongHashSet > manager;

	private final MeshInfos< TLongHashSet > meshInfos;

	private final int numScaleLevels;

	private final NumericSliderWithField scaleSlider;

	private final NumericSliderWithField smoothingLambdaSlider;

	private final NumericSliderWithField smoothingIterationsSlider;

	final ObservableMap< MeshInfo< TLongHashSet >, MeshInfoNode > infoNodesCache = FXCollections.observableHashMap();

	final ObservableList< MeshInfoNode > infoNodes = FXCollections.observableArrayList();

	private final VBox managerSettingsPane;

	private final VBox meshesBox = new VBox();

	private final TitledPane meshesPane = new TitledPane( "Meshes", meshesBox );
	{
//		meshesPane.setMinWidth( 50 );
		meshesPane.setMaxWidth( Double.MAX_VALUE );
	}

	private boolean isBound = false;

	public MeshPane( final MeshManager< TLongHashSet > manager, final MeshInfos< TLongHashSet > meshInfos, final int numScaleLevels )
	{
		super();
		this.manager = manager;
		this.meshInfos = meshInfos;
		this.numScaleLevels = numScaleLevels;

		scaleSlider = new NumericSliderWithField( 0, this.numScaleLevels - 1, manager.scaleLevelProperty().get() );
		smoothingLambdaSlider = new NumericSliderWithField( 0.0, 1.0, 0.5 );
		smoothingIterationsSlider = new NumericSliderWithField( 0, 10, 5 );

		managerSettingsPane = new VBox( new Label( "Defaults" ), setupManagerSliderGrid(), meshesPane );

		this.meshInfos.readOnlyInfos().addListener( this );

	}

	@Override
	public Node get()
	{
		return managerSettingsPane;
	}

	@Override
	public void bind()
	{
		isBound = true;
		this.meshInfos.readOnlyInfos().addListener( this );
		scaleSlider.slider().valueProperty().bindBidirectional( manager.scaleLevelProperty() );
		smoothingLambdaSlider.slider().valueProperty().bindBidirectional( manager.smoothingLambdaProperty() );
		smoothingIterationsSlider.slider().valueProperty().bindBidirectional( manager.smoothingIterationsProperty() );
		new ArrayList<>( this.infoNodes ).forEach( MeshInfoNode::bind );
	}

	@Override
	public void unbind()
	{
		isBound = false;
		this.meshInfos.readOnlyInfos().removeListener( this );
		scaleSlider.slider().valueProperty().unbindBidirectional( manager.scaleLevelProperty() );
		smoothingLambdaSlider.slider().valueProperty().unbindBidirectional( manager.smoothingLambdaProperty() );
		smoothingIterationsSlider.slider().valueProperty().unbindBidirectional( manager.smoothingIterationsProperty() );
		new ArrayList<>( this.infoNodes ).forEach( MeshInfoNode::unbind );
	}

	@Override
	public void onChanged( final Change< ? extends MeshInfo< TLongHashSet > > change )
	{
		while ( change.next() )
		{
			if ( change.wasRemoved() )
			{
				change.getRemoved().forEach( info -> Optional.ofNullable( infoNodesCache.remove( info ) ).ifPresent( MeshInfoNode::unbind ) );
			}
		}
		populateInfoNodes( this.meshInfos.readOnlyInfos() );
	}

	private void populateInfoNodes( final List< MeshInfo< TLongHashSet > > infos )
	{
		final List< MeshInfoNode > infoNodes = new ArrayList<>( infos ).stream().map( this::fromMeshInfo ).collect( Collectors.toList() );
		LOG.debug( "Setting info nodes: {}: ", infoNodes );
		this.infoNodes.setAll( infoNodes );
		final Button exportMeshButton = new Button( "Export all" );
		exportMeshButton.setOnAction( event -> {
			final MeshExporterDialog exportDialog = new MeshExporterDialog( meshInfos );
			final Optional< ExportResult > result = exportDialog.showAndWait();
			if ( result.isPresent() )
			{
				final ExportResult parameters = result.get();

				@SuppressWarnings( "unchecked" )
				final InterruptibleFunction< Long, Interval[] >[][] blockListCaches = Stream
				.generate( manager::blockListCache )
				.limit( meshInfos.readOnlyInfos().size() )
				.toArray( InterruptibleFunction[][]::new );

				final InterruptibleFunction< ShapeKey< Long >, Pair< float[], float[] > >[][] meshCaches = Stream
						.generate( manager::meshCache )
						.limit( meshInfos.readOnlyInfos().size() )
						.toArray( InterruptibleFunction[][]::new );

				parameters.getMeshExporter().exportMesh( blockListCaches, meshCaches, parameters.getSegmentId(), parameters.getScale(), parameters.getFilePaths() );
			}
		} );
		InvokeOnJavaFXApplicationThread.invoke( () -> {
			this.meshesBox.getChildren().setAll( infoNodes.stream().map( MeshInfoNode::get ).collect( Collectors.toList() ) );
			this.meshesBox.getChildren().add( exportMeshButton );
		} );
	}

	private Node setupManagerSliderGrid()
	{

		final GridPane contents = new GridPane();

		int row = 0;
		contents.add( new Label( "Scale" ), 0, row );
		contents.add( scaleSlider.slider(), 1, row );
		contents.add( scaleSlider.textField(), 2, row );
		scaleSlider.slider().setShowTickLabels( true );
		scaleSlider.slider().setTooltip( new Tooltip( "Default for scale level." ) );
		++row;

		contents.add( new Label( "Lambda" ), 0, row );
		contents.add( smoothingLambdaSlider.slider(), 1, row );
		contents.add( smoothingLambdaSlider.textField(), 2, row );
		smoothingLambdaSlider.slider().setShowTickLabels( true );
		smoothingLambdaSlider.slider().setTooltip( new Tooltip( "Default for smoothing lambda." ) );
		++row;

		contents.add( new Label( "Iterations" ), 0, row );
		contents.add( smoothingIterationsSlider.slider(), 1, row );
		contents.add( smoothingIterationsSlider.textField(), 2, row );
		smoothingIterationsSlider.slider().setShowTickLabels( true );
		smoothingIterationsSlider.slider().setTooltip( new Tooltip( "Default for smoothing iterations." ) );
		++row;

		return contents;
	}

	private MeshInfoNode fromMeshInfo( final MeshInfo< TLongHashSet > info )
	{
		final MeshInfoNode node = infoNodesCache.computeIfAbsent( info, MeshInfoNode::new );
		if ( this.isBound )
		{
			node.bind();
			node.isManagedExternally().addListener( (obs, oldv, newv) -> node.bindToExternalSliders(
					scaleSlider.slider().valueProperty(),
					smoothingLambdaSlider.slider().valueProperty(),
					smoothingIterationsSlider.slider().valueProperty(),
					newv ) );
			node.bindToExternalSliders(
					scaleSlider.slider().valueProperty(),
					smoothingLambdaSlider.slider().valueProperty(),
					smoothingIterationsSlider.slider().valueProperty(),
					node.isManagedExternally().get() );
		}
		return node;
	}
}
