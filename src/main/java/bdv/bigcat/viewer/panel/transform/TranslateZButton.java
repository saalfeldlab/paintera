package bdv.bigcat.viewer.panel.transform;

import bdv.bigcat.viewer.panel.ViewerNode.ViewerAxis;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableValue;
import net.imglib2.realtransform.AffineTransform3D;

class TranslateZButton
{

	private final ObservableDoubleValue translationSpeed;

	ObjectProperty< GlobalTransformManager > manager = new SimpleObjectProperty<>();

	private final AffineTransform3D worldToSharedViewerSpace;

	private final int axis;

	private final Object synchronizeObject;

	public TranslateZButton(
			final ObservableDoubleValue translationSpeed,
			final ObservableValue< GlobalTransformManager > manager,
			final AffineTransform3D worldToSharedViewerSpace,
			final ViewerAxis axis,
			final Object synchronizeObject )
	{
		this.translationSpeed = Bindings.createDoubleBinding( () -> translationSpeed.get() * 40, translationSpeed );
		this.manager.bind( manager );
		this.worldToSharedViewerSpace = worldToSharedViewerSpace;
		this.axis = axis.equals( ViewerAxis.X ) ? 0 : axis.equals( ViewerAxis.Y ) ? 1 : 2;
		this.synchronizeObject = synchronizeObject;
	}

	public void translate()
	{
		synchronized ( synchronizeObject )
		{
			final AffineTransform3D affine = worldToSharedViewerSpace.copy();
			final AffineTransform3D rotationAndScalingOnly = affine.copy();
			rotationAndScalingOnly.setTranslation( 0, 0, 0 );
			final double[] delta = new double[ 3 ];
			delta[ axis ] = 1.0;
			rotationAndScalingOnly.applyInverse( delta, delta );
			final double norm = delta[ 0 ] * delta[ 0 ] + delta[ 1 ] * delta[ 1 ] + delta[ 2 ] * delta[ 2 ];
			final double factor = translationSpeed.get() / Math.sqrt( norm );
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
