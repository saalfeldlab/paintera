package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;

import org.controlsfx.control.StatusBar;
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;

public class MeshInfoNode< T > implements BindUnbindAndNodeSupplier
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final MeshInfo< T > meshInfo;

	private final NumericSliderWithField scaleSlider;

	private final NumericSliderWithField smoothingLambdaSlider;

	private final NumericSliderWithField smoothingIterationsSlider;

	private final NumericSliderWithField opacitySlider;

	private final IntegerProperty submittedTasks = new SimpleIntegerProperty( 0 );

	private final IntegerProperty completedTasks = new SimpleIntegerProperty( 0 );

	private final Node contents;

	private final ComboBox< DrawMode > drawModeChoice;

	private final ComboBox< CullFace > cullFaceChoice;

	public MeshInfoNode( final MeshInfo< T > meshInfo )
	{
		super();
		this.meshInfo = meshInfo;
		scaleSlider = new NumericSliderWithField( 0, meshInfo.numScaleLevels() - 1, meshInfo.scaleLevelProperty().get() );
		smoothingLambdaSlider = new NumericSliderWithField( 0.0, 1.0, meshInfo.smoothingLambdaProperty().get() );
		smoothingIterationsSlider = new NumericSliderWithField( 0, 10, meshInfo.smoothingIterationsProperty().get() );
		this.opacitySlider = new NumericSliderWithField( 0, 1.0, meshInfo.opacityProperty().get() );

		this.drawModeChoice = new ComboBox<>( FXCollections.observableArrayList( DrawMode.values() ) );
		this.drawModeChoice.setValue( DrawMode.FILL );

		this.cullFaceChoice = new ComboBox<>( FXCollections.observableArrayList( CullFace.values() ) );
		this.cullFaceChoice.setValue( CullFace.FRONT );

		this.contents = createContents();

	}

	public MeshInfoNode(
			final Long segmentId,
			final FragmentSegmentAssignment assignment,
			final MeshManager< Long, T > meshManager,
			final int numScaleLevels )
	{
		this( new MeshInfo<>( segmentId, assignment, meshManager, numScaleLevels ) );
	}

	@Override
	public void bind()
	{
		scaleSlider.slider().valueProperty().bindBidirectional( meshInfo.scaleLevelProperty() );
		smoothingLambdaSlider.slider().valueProperty().bindBidirectional( meshInfo.smoothingLambdaProperty() );
		smoothingIterationsSlider.slider().valueProperty().bindBidirectional( meshInfo.smoothingIterationsProperty() );
		opacitySlider.slider().valueProperty().bindBidirectional( meshInfo.opacityProperty() );
		drawModeChoice.valueProperty().bindBidirectional( meshInfo.drawModeProperty() );
		cullFaceChoice.valueProperty().bindBidirectional( meshInfo.cullFaceProperty() );
		this.submittedTasks.bind( meshInfo.submittedTasksProperty() );
		this.completedTasks.bind( meshInfo.completedTasksProperty() );
	}

	@Override
	public void unbind()
	{
		scaleSlider.slider().valueProperty().unbindBidirectional( meshInfo.scaleLevelProperty() );
		smoothingLambdaSlider.slider().valueProperty().unbindBidirectional( meshInfo.smoothingLambdaProperty() );
		smoothingIterationsSlider.slider().valueProperty().unbindBidirectional( meshInfo.smoothingIterationsProperty() );
		opacitySlider.slider().valueProperty().unbindBidirectional( meshInfo.opacityProperty() );
		drawModeChoice.valueProperty().unbindBidirectional( meshInfo.drawModeProperty() );
		cullFaceChoice.valueProperty().unbindBidirectional( meshInfo.cullFaceProperty() );
		this.submittedTasks.unbind();
		this.completedTasks.unbind();
	}

	public void bindToExternalSliders(
			final DoubleProperty scaleLevel,
			final DoubleProperty smoothingLambda,
			final DoubleProperty smoothingIterations,
			final DoubleProperty opacity,
			final Property< DrawMode > drawMode,
			final Property< CullFace > cullFace,
			final boolean bind )
	{
		if ( bind )
		{
			bindToExternalSliders( scaleLevel, smoothingLambda, smoothingIterations, opacity, drawMode, cullFace );
		}
		else
		{
			unbindExternalSliders( scaleLevel, smoothingLambda, smoothingIterations, opacity, drawMode, cullFace );
		}
	}

	public void bindToExternalSliders(
			final DoubleProperty scaleLevel,
			final DoubleProperty smoothingLambda,
			final DoubleProperty smoothingIterations,
			final DoubleProperty opacity,
			final Property< DrawMode > drawMode,
			final Property< CullFace > cullFace )
	{
		this.scaleSlider.slider().valueProperty().bindBidirectional( scaleLevel );
		this.smoothingLambdaSlider.slider().valueProperty().bindBidirectional( smoothingLambda );
		this.smoothingIterationsSlider.slider().valueProperty().bindBidirectional( smoothingIterations );
		this.opacitySlider.slider().valueProperty().bindBidirectional( opacity );
		this.drawModeChoice.valueProperty().bindBidirectional( drawMode );
		this.cullFaceChoice.valueProperty().bindBidirectional( cullFace );
	}

	public void unbindExternalSliders(
			final DoubleProperty scaleLevel,
			final DoubleProperty smoothingLambda,
			final DoubleProperty smoothingIterations,
			final DoubleProperty opacity,
			final Property< DrawMode > drawMode,
			final Property< CullFace > cullFace )
	{
		this.scaleSlider.slider().valueProperty().unbindBidirectional( scaleLevel );
		this.smoothingLambdaSlider.slider().valueProperty().unbindBidirectional( smoothingLambda );
		this.smoothingIterationsSlider.slider().valueProperty().unbindBidirectional( smoothingIterations );
		this.opacitySlider.slider().valueProperty().unbindBidirectional( opacity );
		this.drawModeChoice.valueProperty().unbindBidirectional( drawMode );
		this.cullFaceChoice.valueProperty().unbindBidirectional( cullFace );
	}

	@Override
	public Node get()
	{
		return contents;
	}

	private Node createContents()
	{
		final VBox vbox = new VBox();
		final TitledPane pane = new TitledPane( null, vbox );
		pane.setExpanded( false );

		final long[] fragments = meshInfo.meshManager().containedFragments( meshInfo.segmentId() );

		final DoubleProperty progress = new SimpleDoubleProperty( 0 );
		submittedTasks.addListener( ( obs, oldv, newv ) -> progress.set( submittedTasks.intValue() <= 0 ? submittedTasks.intValue() : completedTasks.doubleValue() / submittedTasks.doubleValue() ) );
		completedTasks.addListener( ( obs, oldv, newv ) -> progress.set( submittedTasks.intValue() <= 0 ? submittedTasks.intValue() : completedTasks.doubleValue() / submittedTasks.doubleValue() ) );
		final StatusBar statusBar = new StatusBar();
//		final ProgressBar statusBar = new ProgressBar( 0.0 );
		// TODO come up with better way to ensure proper size of this!
		statusBar.setMinWidth( 200 );
		statusBar.setMaxWidth( 200 );
		statusBar.setPrefWidth( 200 );
		statusBar.setText( "Id" + meshInfo.segmentId() );
		final Tooltip statusToolTip = new Tooltip();
		progress.addListener( ( obs, oldv, newv ) -> InvokeOnJavaFXApplicationThread.invoke( () -> statusToolTip.setText( statusBarToolTipText( submittedTasks.intValue(), completedTasks.intValue() ) ) ) );
		submittedTasks.addListener( obs -> InvokeOnJavaFXApplicationThread.invoke( () -> statusBar.setStyle( progressBarStyleColor( submittedTasks.get() ) ) ) );
		statusBar.setTooltip( statusToolTip );
		statusBar.setProgress( 0.0 );
		progress.addListener( ( obs, oldv, newv ) -> InvokeOnJavaFXApplicationThread.invoke( () -> statusBar.setProgress( Double.isFinite( newv.doubleValue() ) ? newv.doubleValue() : 0.0 ) ) );
		InvokeOnJavaFXApplicationThread.invoke( () -> statusBar.setProgress( Math.max( progress.get(), 0.0 ) ) );
		pane.setGraphic( statusBar );
//		pane.setGraphic( pb );

		final Button exportMeshButton = new Button( "Export" );
		exportMeshButton.setOnAction( event -> {
			final MeshExporterDialog< T > exportDialog = new MeshExporterDialog<>( meshInfo );
			final Optional< ExportResult< T > > result = exportDialog.showAndWait();
			if ( result.isPresent() )
			{
				final ExportResult< T > parameters = result.get();
				parameters.getMeshExporter().exportMesh(
						meshInfo.meshManager().blockListCache(),
						meshInfo.meshManager().meshCache(),
						meshInfo.meshManager().unmodifiableMeshMap().get( parameters.getSegmentId()[ 0 ] ).getId(),
						parameters.getScale(),
						parameters.getFilePaths()[ 0 ] );
			}
		} );

		final Label ids = new Label( Arrays.toString( fragments ) );
		final Label idsLabel = new Label( "ids: " );
		final Tooltip idToolTip = new Tooltip();
		ids.setTooltip( idToolTip );
		idToolTip.textProperty().bind( ids.textProperty() );
		idsLabel.setMinWidth( 30 );
		idsLabel.setMaxWidth( 30 );
		final Region spacer = new Region();
		final HBox idsRow = new HBox( idsLabel, spacer, ids );
		HBox.setHgrow( ids, Priority.ALWAYS );
		HBox.setHgrow( spacer, Priority.ALWAYS );

		vbox.getChildren().addAll( idsRow, exportMeshButton );

		return pane;
	}

	private static String statusBarToolTipText( final int submittedTasks, final int completedTasks )
	{

		return submittedTasks == MeshGenerator.RETRIEVING_RELEVANT_BLOCKS
				? "Retrieving blocks for mesh"
				: submittedTasks == MeshGenerator.SUBMITTED_MESH_GENERATION_TASK
						? "Submitted mesh generation task"
						: ( completedTasks + "/" + submittedTasks );
	}

	private static String progressBarStyleColor( final int submittedTasks )
	{

		if ( submittedTasks == MeshGenerator.SUBMITTED_MESH_GENERATION_TASK )
		{
			LOG.debug( "Submitted tasks={}, changing color to red", submittedTasks );
			return "-fx-accent: red; ";
		}

		if ( submittedTasks == MeshGenerator.RETRIEVING_RELEVANT_BLOCKS )
		{
			LOG.debug( "Submitted tasks={}, changing color to orange", submittedTasks );
			return "-fx-accent: orange; ";
		}

		LOG.debug( "Submitted tasks={}, changing color to green", submittedTasks );
		return "-fx-accent: green; ";
	}

}
