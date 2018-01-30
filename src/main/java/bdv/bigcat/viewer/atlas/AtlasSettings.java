package bdv.bigcat.viewer.atlas;

import java.util.Optional;

import bdv.bigcat.viewer.atlas.mode.Mode;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

public class AtlasSettings
{

	private final BooleanProperty changed = new SimpleBooleanProperty();
	{
		changed.addListener( ( obs, oldv, newv ) -> changed.set( false ) );
	}

	private final BooleanProperty allowRotations = new SimpleBooleanProperty( true );
	{
		allowRotations.addListener( ( obs, oldv, newv ) -> changed.set( true ) );
	}

	private final ObjectProperty< Mode > currentMode = new SimpleObjectProperty<>();
	{
		currentMode.addListener( ( obs, oldv, newv ) -> changed.set( true ) );
		currentMode.addListener( ( obs, oldv, newv ) -> Optional.ofNullable( oldv ).ifPresent( Mode::disable ) );
		currentMode.addListener( ( obs, oldv, newv ) -> Optional.ofNullable( newv ).ifPresent( Mode::enable ) );
	}

	private final ObservableList< Mode > availableModes = FXCollections.observableArrayList();
	{
		availableModes.addListener( ( ListChangeListener< Mode > ) change -> changed.set( true ) );
	}

	private final DoubleProperty zoomSpeed = new SimpleDoubleProperty( 1.05 );
	{
		zoomSpeed.addListener( c -> changed.set( true ) );
	}

	private final DoubleProperty rotationSpeed = new SimpleDoubleProperty( 1 );
	{
		rotationSpeed.addListener( c -> changed.set( true ) );
	}

	private final DoubleProperty translationSpeed = new SimpleDoubleProperty( 1 );
	{
		translationSpeed.addListener( c -> changed.set( true ) );
	}

	public ObservableBooleanValue changedProperty()
	{
		return ReadOnlyBooleanProperty.readOnlyBooleanProperty( changed );
	}

	public BooleanProperty allowRotationsProperty()
	{
		return allowRotations;
	}

	public ObjectProperty< Mode > currentModeProperty()
	{
		return this.currentMode;
	}

	public ObservableList< Mode > availableModes()
	{
		return this.availableModes;
	}

	public DoubleProperty zoomSpeedProperty()
	{
		return this.zoomSpeed;
	}

	public DoubleProperty rotationSpeedProperty()
	{
		return this.rotationSpeed;
	}

	public DoubleProperty translationSpeedProperty()
	{
		return this.translationSpeed;
	}

}
