package org.janelia.saalfeldlab.paintera.serialization;

import org.janelia.saalfeldlab.paintera.state.SourceInfo;

import com.google.gson.annotations.Expose;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;

public class Properties implements TransformListener< AffineTransform3D >
{

	@Expose
	public final SourceInfo sources;

	@Expose
	public final AffineTransform3D globalTransform = new AffineTransform3D();

	@Expose
	public final WindowProperties windowProperties = new WindowProperties();

	private transient final BooleanProperty transformDirty = new SimpleBooleanProperty( false );

	public transient final ObservableBooleanValue isDirty;

	public Properties( final SourceInfo sources )
	{
		super();
		this.sources = sources;
		this.isDirty = transformDirty.or( windowProperties.hasChanged ).or( sources.isDirtyProperty() );
	}

	@Override
	public void transformChanged( final AffineTransform3D transform )
	{
		globalTransform.set( transform );
		transformDirty.set( true );
	}

	public AffineTransform3D globalTransformCopy()
	{
		return this.globalTransform.copy();
	}

	public void setGlobalTransformClean()
	{
		this.transformDirty.set( true );
	}

	public boolean isDirty()
	{
		return isDirty.get();
	}

	public void clean()
	{
		sources.clean();
		setGlobalTransformClean();
		windowProperties.clean();
	}

}
