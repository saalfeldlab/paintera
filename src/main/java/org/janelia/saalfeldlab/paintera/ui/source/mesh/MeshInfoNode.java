package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import java.util.Arrays;
import java.util.Optional;

import org.controlsfx.control.StatusBar;
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.SourceState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfo;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class MeshInfoNode implements BindUnbindAndNodeSupplier
{

	private final MeshInfo meshInfo;

	private final NumericSliderWithField scaleSlider;

	private final NumericSliderWithField smoothingLambdaSlider;

	private final NumericSliderWithField smoothingIterationsSlider;

	private final IntegerProperty submittedTasks = new SimpleIntegerProperty( 0 );

	private final IntegerProperty completedTasks = new SimpleIntegerProperty( 0 );

	private final Node contents;

	public MeshInfoNode( final MeshInfo meshInfo )
	{
		super();
		this.meshInfo = meshInfo;
		scaleSlider = new NumericSliderWithField( 0, meshInfo.numScaleLevels() - 1, meshInfo.scaleLevelProperty().get() );
		smoothingLambdaSlider = new NumericSliderWithField( 0.0, 1.0, meshInfo.smoothingLambdaProperty().get() );
		smoothingIterationsSlider = new NumericSliderWithField( 0, 10, meshInfo.smoothingIterationsProperty().get() );
		this.contents = createContents();
	}

	public MeshInfoNode( final SourceState< ?, ? > state, final long segmentId, final FragmentSegmentAssignment assignment, final MeshManager meshManager, final int numScaleLevels )
	{
		this( new MeshInfo( state, segmentId, assignment, meshManager, numScaleLevels ) );
	}

	@Override
	public void bind()
	{
		scaleSlider.slider().valueProperty().bindBidirectional( meshInfo.scaleLevelProperty() );
		smoothingLambdaSlider.slider().valueProperty().bindBidirectional( meshInfo.smoothingLambdaProperty() );
		smoothingIterationsSlider.slider().valueProperty().bindBidirectional( meshInfo.smoothingIterationsProperty() );
		this.submittedTasks.bind( meshInfo.submittedTasksProperty() );
		this.completedTasks.bind( meshInfo.completedTasksProperty() );
	}

	@Override
	public void unbind()
	{
		scaleSlider.slider().valueProperty().unbindBidirectional( meshInfo.scaleLevelProperty() );
		smoothingLambdaSlider.slider().valueProperty().unbindBidirectional( meshInfo.smoothingLambdaProperty() );
		smoothingIterationsSlider.slider().valueProperty().unbindBidirectional( meshInfo.smoothingIterationsProperty() );
		this.submittedTasks.unbind();
		this.completedTasks.unbind();
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
		contents.add( new Label( "Ids:" ), 0, row );
		contents.add( new Label( Arrays.toString( fragments ) ), 1, row );
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

		final Button exportMeshButton = new Button( "Export" );
		exportMeshButton.setOnAction( event -> {
			final MeshExporterDialog exportDialog = new MeshExporterDialog( meshInfo );
			final Optional< ExportResult > result = exportDialog.showAndWait();
			if ( result.isPresent() )
			{
				final ExportResult parameters = result.get();
				assert parameters.getSegmentId().length == 1;
				parameters.getMeshExporter().exportMesh( meshInfo.state(), parameters.getSegmentId()[ 0 ], parameters.getScale(), parameters.getFilePaths()[ 0 ] );
			}
		} );
		contents.add( exportMeshButton, 2, row );
		++row;

		vbox.getChildren().add( contents );

		return pane;
	}

}
