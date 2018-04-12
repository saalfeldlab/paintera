package bdv.bigcat.viewer.ortho;

import java.util.Collection;
import java.util.function.Consumer;

import bdv.bigcat.viewer.atlas.control.navigation.AffineTransformWithListeners;
import bdv.bigcat.viewer.atlas.control.navigation.DisplayTransformUpdateOnResize;
import bdv.bigcat.viewer.atlas.control.navigation.TransformConcatenator;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.panel.ViewerPanelInOrthoView;
import bdv.bigcat.viewer.panel.ViewerPanelInOrthoView.ViewerAxis;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.cache.CacheControl;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;

public class OrthogonalViews< BR extends Node >
{

	public static class ViewerAndTransforms
	{
		private final ViewerPanelFX viewer;

		private final GlobalTransformManager manager;

		private final AffineTransformWithListeners displayTransform;

		private final AffineTransformWithListeners globalToViewerTransform;

		private final DisplayTransformUpdateOnResize displayTransformUpdate;

		private final TransformConcatenator concatenator;

		public ViewerAndTransforms(
				final ViewerPanelFX viewer,
				final GlobalTransformManager manager,
				final AffineTransformWithListeners displayTransform,
				final AffineTransformWithListeners globalToViewerTransform )
		{
			super();
			this.viewer = viewer;
			this.manager = manager;
			this.displayTransform = displayTransform;
			this.globalToViewerTransform = globalToViewerTransform;

			this.displayTransformUpdate = new DisplayTransformUpdateOnResize( displayTransform, viewer.widthProperty(), viewer.heightProperty(), manager );
			this.displayTransformUpdate.listen();

			this.concatenator = new TransformConcatenator( this.manager, displayTransform, globalToViewerTransform, manager );
			this.concatenator.setTransformListener( viewer );

		}
	}

	private final ResizableGridPane2x2< ViewerPanelFX, ViewerPanelFX, ViewerPanelFX, BR > grid;

	private final GlobalTransformManager manager;

	private final ViewerAndTransforms topLeft;

	private final ViewerAndTransforms topRight;

	private final ViewerAndTransforms bottomLeft;

	public OrthogonalViews(
			final GlobalTransformManager manager,
			final CacheControl cacheControl,
			final ViewerOptions optional,
			final BR bottomRight )
	{
		this.manager = manager;
		this.topLeft = create( this.manager, cacheControl, optional, ViewerAxis.Z );
		this.topRight = create( this.manager, cacheControl, optional, ViewerAxis.Y );
		this.bottomLeft = create( this.manager, cacheControl, optional, ViewerAxis.X );
		this.grid = new ResizableGridPane2x2<>( topLeft.viewer, topRight.viewer, bottomLeft.viewer, bottomRight );
	}

	public ResizableGridPane2x2< ViewerPanelFX, ViewerPanelFX, ViewerPanelFX, BR > grid()
	{
		return this.grid;
	}

	private static ViewerAndTransforms create(
			final GlobalTransformManager manager,
			final CacheControl cacheControl,
			final ViewerOptions optional,
			final ViewerAxis axis )
	{
		final ViewerPanelFX viewer = new ViewerPanelFX( 1, cacheControl, optional );
		final AffineTransformWithListeners displayTransform = new AffineTransformWithListeners();
		final AffineTransformWithListeners globalToViewerTransform = new AffineTransformWithListeners( ViewerPanelInOrthoView.globalToViewer( axis ) );

		return new ViewerAndTransforms( viewer, manager, displayTransform, globalToViewerTransform );
	}

	private void applyToAll( final Consumer< ViewerPanelFX > apply )
	{
		apply.accept( topLeft.viewer );
		apply.accept( topRight.viewer );
		apply.accept( bottomLeft.viewer );
	}

	public void requestRepaint()
	{
		applyToAll( ViewerPanelFX::requestRepaint );
	}

	public void setAllSources( final Collection< ? extends SourceAndConverter< ? > > sources )
	{
		applyToAll( viewer -> viewer.setAllSources( sources ) );
	}

	public < E extends Event > void addEventHandler( final EventType< E > eventType, final EventHandler< E > handler )
	{
		applyToAll( viewer -> viewer.addEventHandler( eventType, handler ) );
	}

	public < E extends Event > void addEventFilter( final EventType< E > eventType, final EventHandler< E > handler )
	{
		applyToAll( viewer -> viewer.addEventFilter( eventType, handler ) );
	}

	public < E extends Event > void removeEventHandler( final EventType< E > eventType, final EventHandler< E > handler )
	{
		applyToAll( viewer -> viewer.removeEventHandler( eventType, handler ) );
	}

	public < E extends Event > void removeEventFilter( final EventType< E > eventType, final EventHandler< E > handler )
	{
		applyToAll( viewer -> viewer.removeEventFilter( eventType, handler ) );
	}

}
