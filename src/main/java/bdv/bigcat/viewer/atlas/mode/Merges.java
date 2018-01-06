package bdv.bigcat.viewer.atlas.mode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import bdv.bigcat.viewer.IdSelector;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.InstallAndRemove;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

public class Merges extends AbstractStateMode
{

	private final SourceInfo sourceInfo;

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove > > mouseAndKeyHandlers = new HashMap<>();

	public static String AMBIGUOUS_SELECTION_MESSAGE = "";

	public Merges( final SourceInfo sourceInfo )
	{
		this.sourceInfo = sourceInfo;
	}

	@Override
	public String getName()
	{
		return "Merge and Split";
	}

	@Override
	protected Consumer< ViewerPanelFX > getOnEnter()
	{
		return t -> {
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{
				final IdSelector selector = new IdSelector( t, sourceInfo, this );
				final List< InstallAndRemove > iars = new ArrayList<>();
				iars.add( selector.selectFragmentWithMaximumCount( "toggle single id", event -> event.isPrimaryButtonDown() && noSpecialKeys( event ) ) );
				iars.add( selector.merge( "merge fragments", event -> event.isPrimaryButtonDown() && shiftOnly( event ) ) );
				iars.add( selector.detach( "detach", event -> event.isSecondaryButtonDown() && shiftOnly( event ) ) );
				iars.add( selector.confirm( "confirm assignments", event -> event.isPrimaryButtonDown() && shiftAndControl( event ) ) );
				this.mouseAndKeyHandlers.put( t, iars );
			}
			t.getDisplay().addHandler( this.mouseAndKeyHandlers.get( t ) );

		};
	}

	@Override
	public Consumer< ViewerPanelFX > onExit()
	{
		return t -> {
			if ( this.mouseAndKeyHandlers.containsKey( t ) )
				t.getDisplay().removeHandler( this.mouseAndKeyHandlers.get( t ) );
		};
	}

	public static boolean noSpecialKeys( final MouseEvent e )
	{
		return !( e.isAltDown() || e.isControlDown() || e.isShiftDown() || e.isMetaDown() );
	}

	public static boolean shiftOnly( final MouseEvent e )
	{
		return e.isShiftDown() && !( e.isAltDown() || e.isControlDown() || e.isMetaDown() );
	}

	public static boolean shiftOnly( final KeyEvent e )
	{
		return e.isShiftDown() && !( e.isAltDown() || e.isControlDown() || e.isMetaDown() );
	}

	public static boolean shiftAndControl( final MouseEvent e )
	{
		return e.isShiftDown() && e.isControlDown() && !( e.isAltDown() || e.isMetaDown() );
	}

}
