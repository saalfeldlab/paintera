package bdv.bigcat.viewer.atlas.mode.paint;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.IdSelector;
import bdv.bigcat.viewer.atlas.data.mask.MaskedSource;
import bdv.bigcat.viewer.atlas.mode.AbstractStateMode;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.EventFX;
import bdv.bigcat.viewer.bdvfx.InstallAndRemove;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.panel.ViewerNode;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.labels.labelset.Label;
import bdv.viewer.Source;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.input.KeyCode;

public class PaintMode extends AbstractStateMode
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove > > mouseAndKeyHandlers = new HashMap<>();

	private final HashMap< ViewerPanelFX, Paint > painters = new HashMap<>();

	private final HashSet< ViewerNode.ViewerAxis > paintableViews = new HashSet<>( Arrays.asList( ViewerNode.ViewerAxis.Z ) );

	private final HashMap< ViewerPanelFX, ViewerNode.ViewerAxis > viewerAxes;

	private final SourceInfo sourceInfo;

	private final KeyTracker keyTracker;

	private final GlobalTransformManager manager;

	private final SimpleDoubleProperty brushRadius = new SimpleDoubleProperty( 5.0 );

	private final SimpleDoubleProperty brushRadiusIncrement = new SimpleDoubleProperty( 1.0 );

	private final Runnable requestRepaint;

	public PaintMode(
			final HashMap< ViewerPanelFX, ViewerNode.ViewerAxis > viewerAxes,
			final SourceInfo sourceInfo,
			final KeyTracker keyTracker,
			final GlobalTransformManager manager,
			final Runnable requestRepaint )
	{
		super();
		this.viewerAxes = viewerAxes;
		this.sourceInfo = sourceInfo;
		this.keyTracker = keyTracker;
		this.manager = manager;
		this.requestRepaint = requestRepaint;
	}

	@Override
	protected Consumer< ViewerPanelFX > getOnEnter()
	{
		return t -> {
//			if ( this.paintableViews.contains( this.viewerAxes.get( t ) ) )
			{
				if ( !this.mouseAndKeyHandlers.containsKey( t ) )
				{
					final IdSelector selector = new IdSelector( t, sourceInfo, this );
					final Paint paint = new Paint( t, viewerAxes.get( t ), sourceInfo, manager, () -> viewerAxes.keySet().forEach( ViewerPanelFX::requestRepaint ) );
					paint.brushRadiusProperty().set( this.brushRadius.get() );
					paint.brushRadiusProperty().bindBidirectional( this.brushRadius );
					paint.brushRadiusIncrementProperty().set( this.brushRadiusIncrement.get() );
					paint.brushRadiusIncrementProperty().bindBidirectional( this.brushRadiusIncrement );
					final ObjectProperty< Source< ? > > currentSource = sourceInfo.currentSourceProperty();
					final ObjectBinding< SelectedIds > currentSelectedIds = Bindings.createObjectBinding(
							() -> sourceInfo.getState( currentSource.get() ).selectedIds().get( this ),
							currentSource );

					final Supplier< Long > paintSelection = () -> {
						final SelectedIds csi = currentSelectedIds.get();

						if ( csi == null )
						{
							LOG.debug( "Source {} does not provide selected ids.", currentSource.get() );
							return null;
						}

						final long[] selection = csi.getActiveIds();

						if ( selection.length != 1 )
						{
							LOG.debug( "Multiple or no ids selected: {}", Arrays.toString( selection ) );
							return null;
						}

						return selection[ 0 ];
					};

					painters.put( t, paint );

					final FloodFill fill = new FloodFill( t, sourceInfo, requestRepaint );

					final RestrictPainting restrictor = new RestrictPainting( t, sourceInfo, requestRepaint );

					final List< InstallAndRemove > iars = new ArrayList<>();
					iars.add( selector.selectFragmentWithMaximumCount( "toggle single id", event -> event.isPrimaryButtonDown() && keyTracker.activeKeyCount() == 0 ) );
//					iars.add( EventFX.MOUSE_PRESSED( "paint test", paint::paintTest, event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) ) );
					iars.add( EventFX.KEY_PRESSED( "show brush overlay", event -> paint.showBrushOverlay(), event -> keyTracker.areKeysDown( KeyCode.SPACE ) ) );
					iars.add( EventFX.KEY_RELEASED( "show brush overlay", event -> paint.hideBrushOverlay(), event -> event.getCode().equals( KeyCode.SPACE ) && !keyTracker.areKeysDown( KeyCode.SPACE ) ) );
					iars.add( EventFX.SCROLL( "change brush size", event -> paint.changeBrushRadius( event.getDeltaY() ), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) ) );
					iars.add( paint.paintLabel( "paint", paintSelection::get, event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) ) );
					iars.add( paint.paintLabel( "erase canvas", () -> Label.TRANSPARENT, event -> event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) ) );
					iars.add( paint.paintLabel( "erase background", () -> Label.BACKGROUND, event -> event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE, KeyCode.SHIFT ) ) );
					iars.add( EventFX.MOUSE_PRESSED( "fill", event -> fill.fillAt( event.getX(), event.getY(), paintSelection::get ), event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT, KeyCode.F ) ) );
					iars.add( EventFX.MOUSE_PRESSED( "restrict", event -> restrictor.restrictTo( event.getX(), event.getY() ), event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT, KeyCode.R ) ) );
//					iars.add( EventFX.MOUSE_PRESSED( "paint click", event -> paint.paint( event, 100 ), event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) ) );
//					iars.add( EventFX.MOUSE_PRESSED( "paint erase", event -> paint.paint( event, Label.INVALID ), event -> event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) ) );

					final SelectNextId nextId = new SelectNextId( sourceInfo, this );
					iars.add( EventFX.KEY_PRESSED( "next id", event -> nextId.getNextId(), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.N ) ) );

					iars.add( EventFX.KEY_PRESSED( "merge canvas", event -> {
						final Source< ? > cs = currentSource.get();
						if ( cs instanceof MaskedSource< ?, ? > )
						{
							LOG.debug( "Merging canvas for source {}", cs );
							final MaskedSource< ?, ? > mcs = ( MaskedSource< ?, ? > ) cs;
							mcs.mergeCanvasIntoBackground();
						}
						event.consume();
					}, event -> keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT, KeyCode.M ) ) );

					this.mouseAndKeyHandlers.put( t, iars );
				}
				this.mouseAndKeyHandlers.get( t ).forEach( handler -> {
					handler.installInto( t );
				} );
			}
//			t.getDisplay().addHandler( this.mouseAndKeyHandlers.get( t ) );

		};
	}

	@Override
	public Consumer< ViewerPanelFX > onExit()
	{
		return t -> {
			if ( this.mouseAndKeyHandlers.containsKey( t ) )
			{
				if ( painters.containsKey( t ) )
					painters.get( t ).setBrushOverlayVisible( false );
				this.mouseAndKeyHandlers.get( t ).forEach( handler -> {
					handler.removeFrom( t );
				} );
			}
//			if ( this.mouseAndKeyHandlers.containsKey( t ) )
//				t.getDisplay().removeHandler( this.mouseAndKeyHandlers.get( t ) );
		};
	}

	@Override
	public String getName()
	{
		return "Paint";
	}

}
