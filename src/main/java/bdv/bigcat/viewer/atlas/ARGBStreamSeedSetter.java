package bdv.bigcat.viewer.atlas;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.atlas.mode.Mode;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.stream.AbstractHighlightingARGBStream;
import bdv.viewer.Source;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Spinner;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class ARGBStreamSeedSetter
{

	private final HashMap< ViewerPanelFX, EventHandler< KeyEvent > > handlers = new HashMap<>();

	private final SourceInfo sourceInfo;

	private final KeyTracker keyTracker;

	private final ObjectProperty< Mode > currentMode = new SimpleObjectProperty<>( null );

	public ARGBStreamSeedSetter( final SourceInfo sourceInfo, final KeyTracker keyTracker, final ObservableValue< Mode > currentMode )
	{
		super();
		this.sourceInfo = sourceInfo;
		this.keyTracker = keyTracker;
		this.currentMode.bind( currentMode );
	}

	public Consumer< ViewerPanelFX > onEnter()
	{
		return t -> {
			if ( !this.handlers.containsKey( t ) )
			{
				final EventHandler< KeyEvent > handler = event -> {
					final Optional< Source< ? > > source = getSource( t );
					if ( source.isPresent() )
					{
						final Optional< ARGBStream > currentStream = sourceInfo.stream( source.get(), currentMode.get() );
						if ( keyTracker.areOnlyTheseKeysDown( KeyCode.C ) )
						{
							changeStream( currentStream, seed -> seed + 1, sourceInfo, source.get() );
							event.consume();
						}
						else if ( keyTracker.areOnlyTheseKeysDown( KeyCode.C, KeyCode.SHIFT ) )
						{
							changeStream( currentStream, seed -> seed - 1, sourceInfo, source.get() );
							event.consume();
						}
						else if ( keyTracker.areOnlyTheseKeysDown( KeyCode.C, KeyCode.SHIFT, KeyCode.CONTROL ) )
						{
							final LongUnaryOperator op = seed -> {
								final Spinner< Integer > spinner = new Spinner<>( Integer.MIN_VALUE, Integer.MAX_VALUE, ( int ) seed );
								final Dialog< Long > dialog = new Dialog<>();
								dialog.getDialogPane().getButtonTypes().addAll( ButtonType.CANCEL, ButtonType.OK );
								dialog.setHeaderText( "Select seed for ARGB stream" );
								dialog.getDialogPane().setContent( spinner );
								dialog.setResultConverter( param -> param.equals( ButtonType.OK ) ? ( long ) spinner.getValue() : seed );
								final long newSeed = dialog.showAndWait().orElse( seed );
								return newSeed;
							};
							changeStream( currentStream, op, sourceInfo, source.get() );
							event.consume();
						}
					}
				};
				this.handlers.put( t, handler );
			}
			t.addEventHandler( KeyEvent.KEY_PRESSED, this.handlers.get( t ) );

		};
	}

	public Consumer< ViewerPanelFX > onExit()
	{
		return t -> {
			t.removeEventHandler( KeyEvent.KEY_PRESSED, this.handlers.get( t ) );
		};
	}

	private static Optional< Source< ? > > getSource( final ViewerPanelFX viewer )
	{
		final ViewerState state = viewer.getState();
		final List< SourceState< ? > > sources = state.getSources();
		if ( state.getCurrentSource() >= 0 )
		{
			final SourceState< ? > source = sources.get( state.getCurrentSource() );
			return Optional.of( source.getSpimSource() );
		}
		else
			return Optional.empty();
	}

	private static boolean changeStream( final Optional< ARGBStream > currentStream, final LongUnaryOperator seedUpdate, final SourceInfo sourceInfo, final Source< ? > source )
	{
		if ( currentStream.isPresent() && currentStream.get() instanceof AbstractHighlightingARGBStream )
		{
			final long seed = ( ( AbstractHighlightingARGBStream ) currentStream.get() ).getSeed();
			final long currentSeed = seedUpdate.applyAsLong( seed );
			if ( currentSeed != seed )
			{
				sourceInfo.forEachStream( source, stream -> {
					if ( stream instanceof AbstractHighlightingARGBStream )
					{
						( ( AbstractHighlightingARGBStream ) stream ).setSeed( currentSeed );
						( ( AbstractHighlightingARGBStream ) stream ).clearCache();
					}
				} );
				return true;
			}
		}
		return false;
	}

}
