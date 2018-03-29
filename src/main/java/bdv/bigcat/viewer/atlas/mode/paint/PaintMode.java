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

import bdv.bigcat.label.Label;
import bdv.bigcat.viewer.IdSelector;
import bdv.bigcat.viewer.atlas.data.mask.CannotPersist;
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
import bdv.viewer.Source;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;

public class PaintMode extends AbstractStateMode
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove< Node > > > mouseAndKeyHandlers = new HashMap<>();

	private final HashMap< ViewerPanelFX, Paint > painters = new HashMap<>();

	private final HashSet< ViewerNode.ViewerAxis > paintableViews = new HashSet<>( Arrays.asList( ViewerNode.ViewerAxis.Z ) );

	private final SourceInfo sourceInfo;

	private final KeyTracker keyTracker;

	private final GlobalTransformManager manager;

	private final SimpleDoubleProperty brushRadius = new SimpleDoubleProperty( 5.0 );

	private final SimpleDoubleProperty brushRadiusIncrement = new SimpleDoubleProperty( 1.0 );

	private final Runnable requestRepaint;

	private final BooleanProperty paint3D = new SimpleBooleanProperty( false );

	private final BooleanBinding paint2D = paint3D.not();

	public PaintMode(
			final SourceInfo sourceInfo,
			final KeyTracker keyTracker,
			final GlobalTransformManager manager,
			final Runnable requestRepaint )
	{
		super();
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
					final Paint paint = new Paint( t, sourceInfo, manager, requestRepaint );
					final Paint2D paint2D = new Paint2D( t, sourceInfo, manager, requestRepaint );
					paint.brushRadiusProperty().set( this.brushRadius.get() );
					paint.brushRadiusProperty().bindBidirectional( this.brushRadius );
					paint.brushRadiusIncrementProperty().set( this.brushRadiusIncrement.get() );
					paint.brushRadiusIncrementProperty().bindBidirectional( this.brushRadiusIncrement );
					paint2D.brushRadiusProperty().bindBidirectional( this.brushRadius );
					paint2D.brushRadiusIncrementProperty().bindBidirectional( this.brushRadiusIncrement );
					final ObjectProperty< Source< ? > > currentSource = sourceInfo.currentSourceProperty();
					final ObjectBinding< SelectedIds > currentSelectedIds = Bindings.createObjectBinding(
							() -> sourceInfo.getState( currentSource.get() ).selectedIdsProperty().get(),
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

					final List< InstallAndRemove< Node > > iars = new ArrayList<>();

					iars.add( selector.selectFragmentWithMaximumCount( "toggle single id", event -> event.isPrimaryButtonDown() && keyTracker.activeKeyCount() == 0 ) );

					iars.add( EventFX.KEY_PRESSED( "show brush overlay", event -> paint.showBrushOverlay(), event -> keyTracker.areKeysDown( KeyCode.SPACE ) ) );
					iars.add( EventFX.KEY_RELEASED( "show brush overlay", event -> paint.hideBrushOverlay(), event -> event.getCode().equals( KeyCode.SPACE ) && !keyTracker.areKeysDown( KeyCode.SPACE ) ) );
					iars.add( EventFX.SCROLL( "change brush size", event -> paint.changeBrushRadius( event.getDeltaY() ), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) ) );

					iars.add( EventFX.MOUSE_PRESSED( "paint click", paint::paint, event -> keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) && this.paint3D.get() ) );
					iars.add( EventFX.MOUSE_PRESSED( "paint click 2D", paint::paint, event -> keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) && this.paint2D.get() ) );

					iars.add( paint.dragPaintLabel( "paint", paintSelection::get, event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) && this.paint3D.get() ) );
					iars.add( paint2D.dragPaintLabel( "paint 2D", paintSelection::get, event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) && this.paint2D.get() ) );

					iars.add( paint.dragPaintLabel( "erase canvas", () -> Label.TRANSPARENT, event -> event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) && this.paint3D.get() ) );
					iars.add( paint2D.dragPaintLabel( "erase canvas 2D", () -> Label.TRANSPARENT, event -> event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE ) && this.paint2D.get() ) );

					iars.add( paint.dragPaintLabel( "to background", () -> Label.BACKGROUND, event -> event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE, KeyCode.SHIFT ) && this.paint3D.get() ) );
					iars.add( paint2D.dragPaintLabel( "to background 2D", () -> Label.BACKGROUND, event -> event.isSecondaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SPACE, KeyCode.SHIFT ) && this.paint2D.get() ) );

					iars.add( EventFX.MOUSE_PRESSED( "fill", event -> fill.fillAt( event.getX(), event.getY(), paintSelection::get ), event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT, KeyCode.F ) ) );
					iars.add( EventFX.MOUSE_PRESSED( "restrict", event -> restrictor.restrictTo( event.getX(), event.getY() ), event -> event.isPrimaryButtonDown() && keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT, KeyCode.R ) ) );

					final SelectNextId nextId = new SelectNextId( sourceInfo );
					iars.add( EventFX.KEY_PRESSED( "next id", event -> nextId.getNextId(), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.N ) ) );

					iars.add( EventFX.KEY_PRESSED( "merge canvas", event -> {
						final Source< ? > cs = currentSource.get();
						if ( cs instanceof MaskedSource< ?, ? > )
						{
							LOG.debug( "Merging canvas for source {}", cs );
							final MaskedSource< ?, ? > mcs = ( MaskedSource< ?, ? > ) cs;
							try
							{
								mcs.persistCanvas();
							}
							catch ( final CannotPersist e )
							{
								LOG.warn( "Could not persist canvas. Try again later." );
							}
						}
						event.consume();
					}, event -> keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT, KeyCode.M ) ) );

					this.mouseAndKeyHandlers.put( t, iars );
				}
				this.mouseAndKeyHandlers.get( t ).forEach( handler -> {
					handler.installInto( t );
				} );
			}

		};
	}

	public BooleanProperty paint3DProperty()
	{
		return this.paint3D;
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
		};
	}

	@Override
	public String getName()
	{
		return "Paint";
	}

}
