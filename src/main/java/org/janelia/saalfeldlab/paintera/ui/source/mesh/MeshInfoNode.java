package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import java.util.Arrays;
import java.util.Optional;

import org.controlsfx.control.StatusBar;
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.DrawMode;

public class MeshInfoNode< T > implements BindUnbindAndNodeSupplier
{

	private final MeshInfo< T > meshInfo;

	private final NumericSliderWithField scaleSlider;

	private final NumericSliderWithField smoothingLambdaSlider;

	private final NumericSliderWithField smoothingIterationsSlider;

	private final NumericSliderWithField opacitySlider;

	private final IntegerProperty submittedTasks = new SimpleIntegerProperty( 0 );

	private final IntegerProperty completedTasks = new SimpleIntegerProperty( 0 );

	private final Node contents;

	private final BooleanProperty manageSettings = new SimpleBooleanProperty( false );

	private final BooleanBinding isManagedExternally = manageSettings.not();

	private final ComboBox< DrawMode > drawModeChoice;

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
		this.contents = createContents();

	}

	public MeshInfoNode(
			final long segmentId,
			final FragmentSegmentAssignment assignment,
			final MeshManager< T > meshManager,
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
		this.drawModeChoice.valueProperty().bindBidirectional( meshInfo.drawModeProperty() );
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
		this.drawModeChoice.valueProperty().unbindBidirectional( meshInfo.drawModeProperty() );
		this.submittedTasks.unbind();
		this.completedTasks.unbind();
	}

	public void bindToExternalSliders(
			final DoubleProperty scaleLevel,
			final DoubleProperty smoothingLambda,
			final DoubleProperty smoothingIterations,
			final DoubleProperty opacity,
			final Property< DrawMode > drawMode,
			final boolean bind
			)
	{
		if ( bind )
		{
			bindToExternalSliders( scaleLevel, smoothingLambda, smoothingIterations, opacity, drawMode );
		}
		else
		{
			unbindExternalSliders( scaleLevel, smoothingLambda, smoothingIterations, opacity, drawMode );
		}
	}

	public void bindToExternalSliders(
			final DoubleProperty scaleLevel,
			final DoubleProperty smoothingLambda,
			final DoubleProperty smoothingIterations,
			final DoubleProperty opacity,
			final Property< DrawMode > drawMode
			)
	{
		this.scaleSlider.slider().valueProperty().bindBidirectional( scaleLevel );
		this.smoothingLambdaSlider.slider().valueProperty().bindBidirectional( smoothingLambda );
		this.smoothingIterationsSlider.slider().valueProperty().bindBidirectional( smoothingIterations );
		this.opacitySlider.slider().valueProperty().bindBidirectional( opacity );
		this.drawModeChoice.valueProperty().bindBidirectional( drawMode );
	}

	public void unbindExternalSliders(
			final DoubleProperty scaleLevel,
			final DoubleProperty smoothingLambda,
			final DoubleProperty smoothingIterations,
			final DoubleProperty opacity,
			final Property< DrawMode > drawMode
			)
	{
		this.scaleSlider.slider().valueProperty().unbindBidirectional( scaleLevel );
		this.smoothingLambdaSlider.slider().valueProperty().unbindBidirectional( smoothingLambda );
		this.smoothingIterationsSlider.slider().valueProperty().unbindBidirectional( smoothingIterations );
		this.opacitySlider.slider().valueProperty().unbindBidirectional( opacity );
		this.drawModeChoice.valueProperty().unbindBidirectional( drawMode );
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

		final long[] fragments = meshInfo.assignment().getFragments( meshInfo.segmentId() ).toArray();

		final DoubleProperty progress = new SimpleDoubleProperty( 0 );
		submittedTasks.addListener( ( obs, oldv, newv ) -> progress.set( submittedTasks.intValue() == 0 ? 0 : completedTasks.doubleValue() / submittedTasks.doubleValue() ) );
		completedTasks.addListener( ( obs, oldv, newv ) -> progress.set( submittedTasks.intValue() == 0 ? 0 : completedTasks.doubleValue() / submittedTasks.doubleValue() ) );
		final StatusBar statusBar = new StatusBar();
		// TODO come up with better way to ensure proper size of this!
		statusBar.setMinWidth( 200 );
		statusBar.setMaxWidth( 200 );
		statusBar.setPrefWidth( 200 );
		statusBar.setText( "Id" + meshInfo.segmentId() );
		final Tooltip statusToolTip = new Tooltip();
		progress.addListener( ( obs, oldv, newv ) -> InvokeOnJavaFXApplicationThread.invoke( () -> statusToolTip.setText( completedTasks.intValue() + "/" + submittedTasks.intValue() ) ) );
		statusBar.setTooltip( statusToolTip );
		statusBar.setProgress( 1.0 );
		progress.addListener( ( obs, oldv, newv ) -> statusBar.setProgress( Double.isFinite( newv.doubleValue() ) ? newv.doubleValue() : 1.0 ) );
		pane.setGraphic( statusBar );

		final GridPane contents = new GridPane();

		int row = 0;

		contents.add( new Label( "Opacity " ), 0, row );
		contents.add( opacitySlider.slider(), 1, row );
		contents.add( opacitySlider.textField(), 2, row );
		opacitySlider.slider().setShowTickLabels( true );
		opacitySlider.slider().setTooltip( new Tooltip( "Mesh opacity")  );
		++row;

		contents.add( new Label( "Scale" ), 0, row );
		contents.add( scaleSlider.slider(), 1, row );
		contents.add( scaleSlider.textField(), 2, row );
		scaleSlider.slider().setShowTickLabels( true );
		scaleSlider.slider().setTooltip( new Tooltip( "Render meshes at scale level" ) );
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
		smoothingIterationsSlider.slider().setTooltip( new Tooltip( "Smooth meshes n times." ) );
		++row;

		contents.add( new Label("Draw Mode"), 0, row );
		contents.add( drawModeChoice, 2, row );
		++row;

		final Button exportMeshButton = new Button( "Export" );
		exportMeshButton.setOnAction( event -> {
			final MeshExporterDialog< T > exportDialog = new MeshExporterDialog<>( meshInfo );
			final Optional< ExportResult< T > > result = exportDialog.showAndWait();
			if ( result.isPresent() )
			{
				final ExportResult< T > parameters = result.get();
				assert parameters.getSegmentId().length == 1;
				parameters.getMeshExporter().exportMesh(
						meshInfo.meshManager().blockListCache(),
						meshInfo.meshManager().meshCache(),
						this.meshInfo.meshManager().containedFragments( parameters.getSegmentId()[ 0 ] ),
						parameters.getSegmentId()[ 0 ],
						parameters.getScale(),
						parameters.getFilePaths()[ 0 ] );
			}
		} );

		final CheckBox individualSettingsSwitch = new CheckBox( "Individual settings" );
		individualSettingsSwitch.setSelected( false );
		this.manageSettings.bind( individualSettingsSwitch.selectedProperty() );
		contents.visibleProperty().bind( this.manageSettings );


		final Label ids = new Label( Arrays.toString( fragments ) );
		final Label idsLabel = new Label( "ids: " );
		final Tooltip idToolTip = new Tooltip();
		ids.setTooltip( idToolTip );
		idToolTip.textProperty().bind( ids.textProperty() );
		idsLabel.setMinWidth( 30 );
		idsLabel.setMaxWidth( 30 );
		final Region spacer = new Region();
		final HBox idsRow = new HBox ( idsLabel, spacer, ids );
		HBox.setHgrow( ids, Priority.ALWAYS );
		HBox.setHgrow( spacer, Priority.ALWAYS );


		vbox.getChildren().addAll( idsRow, individualSettingsSwitch, exportMeshButton );

		this.manageSettings.addListener( (obs, oldv, newv  ) -> {
			if ( newv )
			{
				vbox.getChildren().setAll( idsRow, individualSettingsSwitch, contents, exportMeshButton );
			}
			else
			{
				vbox.getChildren().setAll( idsRow, individualSettingsSwitch, exportMeshButton );
			}
		} );

		return pane;
	}

	public ObservableBooleanValue isManagedExternally()
	{
		return this.isManagedExternally;
	}

}
