package org.janelia.saalfeldlab.paintera.serialization;

import java.util.Optional;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.janelia.saalfeldlab.util.MakeUnchecked.CheckedConsumer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;

public class Properties implements TransformListener< AffineTransform3D >
{

	private static final String SOURCES_KEY = "sources";

	private static final String GLOBAL_TRANSFORM_KEY = "globalTransform";

	private static final String WINDOW_PROPERTIES_KEY = "windowProperties";

	@Expose
	public final SourceInfo sources;

	@Expose
	public final AffineTransform3D globalTransform = new AffineTransform3D();

	@Expose
	public final WindowProperties windowProperties = new WindowProperties();

	private transient final BooleanProperty transformDirty = new SimpleBooleanProperty( false );

	public transient final ObservableBooleanValue isDirty;

	public Properties( final PainteraBaseView viewer )
	{
		this( viewer.sourceInfo() );
		viewer.manager().addListener( this );
	}

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

	public static Properties fromSerializedProperties(
			final JsonObject serializedProperties,
			final PainteraBaseView viewer,
			final boolean removeExistingSources )
	{
		return fromSerializedProperties( serializedProperties, viewer, removeExistingSources, GsonHelpers.builderWithAllRequiredAdapters().create() );
	}

	public static Properties fromSerializedProperties(
			final JsonObject serializedProperties,
			final PainteraBaseView viewer,
			final boolean removeExistingSources,
			final Gson gson )
	{

		final Properties properties = new Properties( viewer );

		if ( removeExistingSources )
		{
			properties.sources.removeAllSources();
		}

		Optional
				.ofNullable( serializedProperties.get( SOURCES_KEY ) )
				.ifPresent( MakeUnchecked.unchecked( ( CheckedConsumer< JsonElement > ) element -> SourceInfoSerializer.populate(
						viewer::addState,
						properties.sources.currentSourceIndexProperty()::set,
						element.getAsJsonObject(),
						viewer.getQueue(),
						0,
						viewer.viewer3D().meshesGroup(),
						viewer.getPropagationQueue(),
						viewer.getMeshManagerExecutorService(),
						viewer.getMeshWorkerExecutorService(),
						gson ) ) );

		Optional
				.ofNullable( serializedProperties.get( GLOBAL_TRANSFORM_KEY ) )
				.map( element -> gson.fromJson( element, AffineTransform3D.class ) )
				.ifPresent( viewer.manager()::setTransform );

		properties.clean();

		return properties;
	}

}
