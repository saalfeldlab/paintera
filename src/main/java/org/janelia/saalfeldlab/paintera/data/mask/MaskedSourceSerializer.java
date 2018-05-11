package org.janelia.saalfeldlab.paintera.data.mask;

import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.numeric.integer.UnsignedLongType;

public class MaskedSourceSerializer implements JsonSerializer< MaskedSource< ?, ? > >, JsonDeserializer< MaskedSource< ?, ? > >
{

	private static final String UNDERLYING_SOURCE_CLASS_KEY = "sourceClass";

	private static final String UNDERLYING_SOURCE_KEY = "source";

	private static final String CURRENT_CACHE_DIR_KEY = "cacheDir";

	private static final String PERSIST_CANVAS_CLASS_KEY = "persistCanvasClass";

	private static final String PERSIST_CANVAS_KEY = "persistCanvas";

	private final Supplier< String > currentProjectDirectory;

	private final ExecutorService propagationExecutor;

	public MaskedSourceSerializer( Supplier< String > currentProjectDirectory, ExecutorService propagationExecutor )
	{
		super();
		this.currentProjectDirectory = currentProjectDirectory;
		this.propagationExecutor = propagationExecutor;
	}

	@Override
	public JsonElement serialize( MaskedSource< ?, ? > src, Type type, JsonSerializationContext context )
	{
		JsonObject map = new JsonObject();
		map.add( UNDERLYING_SOURCE_KEY, context.serialize( src.underlyingSource() ) );
		map.addProperty( UNDERLYING_SOURCE_CLASS_KEY, src.underlyingSource().getClass().getName() );
		map.addProperty( CURRENT_CACHE_DIR_KEY, Paths.get( currentProjectDirectory.get() ).relativize( Paths.get( src.currentCanvasDirectory() ) ).toString() );
		map.addProperty( PERSIST_CANVAS_CLASS_KEY, src.getPersister().getClass().getName() );
		map.add( PERSIST_CANVAS_KEY, context.serialize( src.getPersister(), src.getPersister().getClass() ) );
		return map;
	}

	@Override
	public MaskedSource< ?, ? > deserialize( JsonElement el, Type type, JsonDeserializationContext context ) throws JsonParseException
	{
		try
		{
		JsonObject map = el.getAsJsonObject();
		TmpDirectoryCreator canvasCacheDirUpdate = new TmpDirectoryCreator( Paths.get( currentProjectDirectory.get() ), null );

		String sourceClass = map.get( UNDERLYING_SOURCE_CLASS_KEY ).getAsString();
		DataSource< ?, ? > source = context.deserialize( map.get( UNDERLYING_SOURCE_KEY ), Class.forName( sourceClass ) );

		final String persisterClass = map.get( PERSIST_CANVAS_CLASS_KEY ).getAsString();
		@SuppressWarnings( "unchecked" )
		final BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > mergeCanvasIntoBackground =
				( BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > ) context.deserialize(
						map.get( PERSIST_CANVAS_KEY ),
						Class.forName( persisterClass ) );

		final String initialCanvasPath = map.get( CURRENT_CACHE_DIR_KEY ).getAsString();

		DataSource< ?, ? > masked = Masks.mask( source, initialCanvasPath, canvasCacheDirUpdate, mergeCanvasIntoBackground, propagationExecutor );
		return masked instanceof MaskedSource< ?, ? >
				? ( MaskedSource< ?, ? > ) masked
				: null;
		}
		catch ( ClassNotFoundException e )
		{
			throw new JsonParseException( e );
		}
	}

	public static class Factory implements StatefulSerializer.SerializerAndDeserializer< MaskedSource< ?, ? >, MaskedSourceSerializer >
	{

		@Override
		public MaskedSourceSerializer create( PainteraBaseView state, Supplier< String > projectDirectory )
		{
			return new MaskedSourceSerializer( projectDirectory, state.getPropagationQueue() );
		}

	}

}
