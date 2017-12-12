package bdv.bigcat.viewer.panel.transform;

import java.util.Arrays;
import java.util.function.Predicate;

import bdv.bigcat.viewer.panel.ViewerNode.ViewerAxis;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.ScrollEvent;
import net.imglib2.realtransform.AffineTransform3D;

class TranslateZ
{

	private final ObservableDoubleValue rotationSpeed;

	ObjectProperty< GlobalTransformManager > manager = new SimpleObjectProperty<>();

	private final AffineTransform3D worldToSharedViewerSpace;

	private final int axis;

	private final double speedFactor;

	private final Predicate< ScrollEvent >[] eventFilter;

	private final Object synchronizeObject;

	public TranslateZ(
			final ObservableDoubleValue rotationSpeed,
			final ObservableValue< GlobalTransformManager > manager,
			final AffineTransform3D worldToSharedViewerSpace,
			final ViewerAxis axis,
			final double speedFactor,
			final Object synchronizeObject,
			final Predicate< ScrollEvent >... eventFilter )
	{
		this.rotationSpeed = rotationSpeed;
		this.manager.bind( manager );
		this.worldToSharedViewerSpace = worldToSharedViewerSpace;
		this.speedFactor = speedFactor;
		this.eventFilter = eventFilter;
		this.axis = axis.equals( ViewerAxis.X ) ? 0 : axis.equals( ViewerAxis.Y ) ? 1 : 2;
		this.synchronizeObject = synchronizeObject;
	}

	public void scroll( final ScrollEvent event )
	{
		final double wheelRotation = event.getDeltaY();
		if ( Arrays.stream( eventFilter ).filter( filter -> filter.test( event ) ).count() > 0 )
			synchronized ( synchronizeObject )
			{
				final AffineTransform3D affine = worldToSharedViewerSpace.copy();
				final AffineTransform3D rotationAndScalingOnly = affine.copy();
				rotationAndScalingOnly.setTranslation( 0, 0, 0 );
				final double[] delta = new double[ 3 ];
				System.out.println( "SETTING FOR AXIS " + axis );
				delta[ axis ] = 1.0;
				rotationAndScalingOnly.applyInverse( delta, delta );
				final double norm = delta[ 0 ] * delta[ 0 ] + delta[ 1 ] * delta[ 1 ] + delta[ 2 ] * delta[ 2 ];
				final double factor = rotationSpeed.get() * speedFactor * -wheelRotation / Math.sqrt( norm );
				for ( int d = 0; d < delta.length; ++d )
					delta[ d ] *= factor;
				final AffineTransform3D shift = new AffineTransform3D();
				shift.setTranslation( delta );
				manager.get().setTransform( affine.concatenate( shift ) );
				// TODO should we translate on world coordinate level (as we do
				// now) or after transformations are applied?
			}
	}
}
