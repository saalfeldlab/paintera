package bdv.bigcat.viewer.atlas;

import bdv.bigcat.viewer.viewer3d.Viewer3D;
import bdv.viewer.ViewerPanel;
import bdv.viewer.state.ViewerState;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;

public class MoveCameraToIntersectionPoint
{

	private final ViewerPanel viewer;

	private final Viewer3D renderView;

	public MoveCameraToIntersectionPoint( final ViewerPanel viewer, final Viewer3D renderView )
	{
		super();
		this.viewer = viewer;
		this.renderView = renderView;
	}

	public void moveToIntersection()
	{
		final AffineTransform3D tf = new AffineTransform3D();
		final ViewerState state;
		final int w;
		final int h;
		synchronized ( viewer )
		{
			state = viewer.getState();
			w = viewer.getWidth();
			h = viewer.getHeight();
		}
		state.getViewerTransform( tf );
		final RealPoint p = new RealPoint( w / 2, h / 2, 0 );
		tf.applyInverse( p, p );
		this.renderView.setCameraPosition( p );
	}

}
