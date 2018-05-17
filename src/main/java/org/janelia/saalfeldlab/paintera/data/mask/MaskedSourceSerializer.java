package org.janelia.saalfeldlab.paintera.data.mask;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class MaskedSourceSerializer implements JsonSerializer< MaskedSource< ?, ? > >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String UNDERLYING_SOURCE_CLASS_KEY = "sourceClass";

	private static final String UNDERLYING_SOURCE_KEY = "source";

	private static final String CURRENT_CACHE_DIR_KEY = "cacheDir";

	private static final String PERSIST_CANVAS_CLASS_KEY = "persistCanvasClass";

	private static final String PERSIST_CANVAS_KEY = "persistCanvas";

	@Override
	public JsonElement serialize( final MaskedSource< ?, ? > src, final Type type, final JsonSerializationContext context )
	{
		final JsonObject map = new JsonObject();
		map.add( UNDERLYING_SOURCE_KEY, context.serialize( src.underlyingSource() ) );
		map.addProperty( UNDERLYING_SOURCE_CLASS_KEY, src.underlyingSource().getClass().getName() );
		LOG.warn( "Not using relative directory for canvas directory!" );
		map.addProperty( CURRENT_CACHE_DIR_KEY, src.currentCanvasDirectory() );
//		LOG.warn( "Trying to relativize '{}' and '{}'", currentProjectDirectory.get(), src.currentCanvasDirectory() );
//		map.addProperty( CURRENT_CACHE_DIR_KEY, Paths.get( currentProjectDirectory.get() ).relativize( Paths.get( src.currentCanvasDirectory() ) ).toString() );
		map.addProperty( PERSIST_CANVAS_CLASS_KEY, src.getPersister().getClass().getName() );
		map.add( PERSIST_CANVAS_KEY, context.serialize( src.getPersister(), src.getPersister().getClass() ) );
		return map;
	}

}
