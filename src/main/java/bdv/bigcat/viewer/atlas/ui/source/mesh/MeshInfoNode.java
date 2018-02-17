package bdv.bigcat.viewer.atlas.ui.source.mesh;

import java.util.Arrays;

import org.controlsfx.control.StatusBar;

import bdv.bigcat.viewer.atlas.ui.BindUnbindAndNodeSupplier;
import bdv.bigcat.viewer.meshes.MeshInfo;
import bdv.bigcat.viewer.meshes.MeshManager;
import bdv.bigcat.viewer.state.FragmentSegmentAssignment;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.bigcat.viewer.util.ui.NumericSliderWithField;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class MeshInfoNode implements BindUnbindAndNodeSupplier
{

	private final MeshInfo meshInfo;

	private final NumericSliderWithField scaleSlider;

	private final NumericSliderWithField simplificationSlider;

	private final IntegerProperty submittedTasks = new SimpleIntegerProperty( 0 );

	private final IntegerProperty completedTasks = new SimpleIntegerProperty( 0 );

	private final Node contents;

	public MeshInfoNode( final MeshInfo meshInfo )
	{
		super();
		this.meshInfo = meshInfo;
		scaleSlider = new NumericSliderWithField( 0, meshInfo.numScaleLevels() - 1, meshInfo.scaleLevelProperty().get() );
		simplificationSlider = new NumericSliderWithField( 0, 10, meshInfo.simplificationIterationsProperty().get() );
		this.contents = createContents();
	}

	public MeshInfoNode( final long segmentId, final FragmentSegmentAssignment assignment, final MeshManager meshManager, final int numScaleLevels )
	{
		this( new MeshInfo( segmentId, assignment, meshManager, numScaleLevels ) );
	}

	@Override
	public void bind()
	{
		scaleSlider.slider().valueProperty().bindBidirectional( meshInfo.scaleLevelProperty() );
		simplificationSlider.slider().valueProperty().bindBidirectional( meshInfo.simplificationIterationsProperty() );
		this.submittedTasks.bind( meshInfo.submittedTasksProperty() );
		this.completedTasks.bind( meshInfo.completedTasksProperty() );
	}

	@Override
	public void unbind()
	{
		scaleSlider.slider().valueProperty().unbindBidirectional( meshInfo.scaleLevelProperty() );
		simplificationSlider.slider().valueProperty().unbindBidirectional( meshInfo.simplificationIterationsProperty() );
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
		submittedTasks.addListener( ( obs, oldv, newv ) -> progress.set( completedTasks.doubleValue() / submittedTasks.doubleValue() ) );
		completedTasks.addListener( ( obs, oldv, newv ) -> progress.set( completedTasks.doubleValue() / submittedTasks.doubleValue() ) );
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

		contents.add( new Label( "Iterations" ), 0, row );
		contents.add( simplificationSlider.slider(), 1, row );
		contents.add( simplificationSlider.textField(), 2, row );
		simplificationSlider.slider().setShowTickLabels( true );
		simplificationSlider.slider().setTooltip( new Tooltip( "Simplify meshes n times." ) );
		++row;

		vbox.getChildren().add( contents );

		return pane;
	}

}
