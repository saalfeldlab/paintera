package bdv.bigcat.viewer.atlas.control;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.bigcat.viewer.atlas.control.navigation.AffineTransformWithListeners;
import bdv.bigcat.viewer.atlas.control.navigation.TranslateXY;
import bdv.bigcat.viewer.bdvfx.InstallAndRemove;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.MouseDragFX;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import javafx.scene.Node;

public class Navigation implements ToOnEnterOnExit
{

	private final GlobalTransformManager manager;

	private final KeyTracker keyTracker;

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove< Node > > > mouseAndKeyHandlers = new HashMap<>();

	private final Function< ViewerPanelFX, AffineTransformWithListeners > displayTransform;

	private final Function< ViewerPanelFX, AffineTransformWithListeners > globalToViewerTransform;

	public Navigation(
			final GlobalTransformManager manager,
			final Function< ViewerPanelFX, AffineTransformWithListeners > displayTransform,
			final Function< ViewerPanelFX, AffineTransformWithListeners > globalToViewerTransform,
			final KeyTracker keyTracker )
	{
		super();
		this.manager = manager;
		this.displayTransform = displayTransform;
		this.globalToViewerTransform = globalToViewerTransform;
		this.keyTracker = keyTracker;
	}

	@Override
	public Consumer< ViewerPanelFX > getOnEnter()
	{
		return t -> {
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{
				final TranslateXY translateXY = new TranslateXY(
						manager,
						this.displayTransform.apply( t ),
						this.globalToViewerTransform.apply( t ),
						manager );
				final List< InstallAndRemove< Node > > iars = new ArrayList<>();
				iars.add( MouseDragFX.createDrag(
						"translate xy",
						e -> e.isSecondaryButtonDown() && keyTracker.noKeysActive(),
						true,
						manager,
						e -> translateXY.init(),
						( dX, dY ) -> translateXY.drag( dX, dY ),
						false ) );
				this.mouseAndKeyHandlers.put( t, iars );
			}
//			t.getDisplay().addHandler( this.mouseAndKeyHandlers.get( t ) );
			this.mouseAndKeyHandlers.get( t ).forEach( iar -> iar.installInto( t ) );
		};
	}

	@Override
	public Consumer< ViewerPanelFX > getOnExit()
	{
		return t -> {
			Optional
					.ofNullable( this.mouseAndKeyHandlers.get( t ) )
					.ifPresent( hs -> hs.forEach( h -> h.removeFrom( t ) ) );
		};
	}

}
