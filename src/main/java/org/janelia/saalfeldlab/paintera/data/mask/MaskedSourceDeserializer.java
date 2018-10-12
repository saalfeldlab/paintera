package org.janelia.saalfeldlab.paintera.data.mask;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.persist.PersistCanvas;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer;
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer.Arguments;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaskedSourceDeserializer implements JsonDeserializer<MaskedSource<?, ?>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String UNDERLYING_SOURCE_CLASS_KEY = MaskedSourceSerializer.UNDERLYING_SOURCE_CLASS_KEY;

	private static final String UNDERLYING_SOURCE_KEY = MaskedSourceSerializer.UNDERLYING_SOURCE_KEY;

	private static final String CURRENT_CACHE_DIR_KEY = MaskedSourceSerializer.CURRENT_CACHE_DIR_KEY;

	private static final String PERSIST_CANVAS_CLASS_KEY = MaskedSourceSerializer.PERSIST_CANVAS_CLASS_KEY;

	private static final String PERSIST_CANVAS_KEY = MaskedSourceSerializer.PERSIST_CANVAS_KEY;

	private final Supplier<String> currentProjectDirectory;

	private final ExecutorService propagationExecutor;

	public MaskedSourceDeserializer(final Supplier<String> currentProjectDirectory, final ExecutorService
			propagationExecutor)
	{
		super();
		this.currentProjectDirectory = currentProjectDirectory;
		this.propagationExecutor = propagationExecutor;
	}

	@Override
	public MaskedSource<?, ?> deserialize(final JsonElement el, final Type type, final JsonDeserializationContext
			context)
	throws JsonParseException
	{
		try
		{
			final JsonObject       map                  = el.getAsJsonObject();
			final Supplier<String> canvasCacheDirUpdate = Masks.canvasTmpDirDirectorySupplier(currentProjectDirectory
					.get());

			final String           sourceClass = map.get(UNDERLYING_SOURCE_CLASS_KEY).getAsString();
			final DataSource<?, ?> source      = context.deserialize(
					map.get(UNDERLYING_SOURCE_KEY),
					Class.forName(sourceClass)
			                                                        );

			final String persisterClass = map.get(PERSIST_CANVAS_CLASS_KEY).getAsString();
			@SuppressWarnings("unchecked") final PersistCanvas
					mergeCanvasIntoBackground =
					context.deserialize(
							map.get(PERSIST_CANVAS_KEY),
							Class.forName(persisterClass)
					                                                                            );

			final String initialCanvasPath = canvasCacheDirUpdate.get();
			// TODO re-use canvas
			//					Optional
			//					.ofNullable( map.get( CURRENT_CACHE_DIR_KEY ) )
			//					.map( JsonElement::getAsString )
			//					.orElseGet( canvasCacheDirUpdate );

			final DataSource<?, ?> masked = Masks.mask(
					source,
					initialCanvasPath,
					canvasCacheDirUpdate,
					mergeCanvasIntoBackground,
					propagationExecutor
			                                          );
			final MaskedSource<?, ?> returnVal = masked instanceof MaskedSource<?, ?>
			                                     ? (MaskedSource<?, ?>) masked
			                                     : null;

			if (returnVal != null)
			{
				final Type mapType = new TypeToken<HashMap<Long, long[]>[]>()
				{}.getType();
				final long[] blocks = Optional.ofNullable((long[]) context.deserialize(
						map.get(MaskedSourceSerializer.DIRTY_BLOCKS_KEY),
						long[].class
				                                                                      )).orElseGet(() -> new long[]
						{});
				final Map<Long, long[]>[] blocksById = Optional
						.ofNullable((Map<Long, long[]>[]) context.deserialize(
								map.get(MaskedSourceSerializer.DIRTY_BLOCKS_BY_ID_KEY),
								mapType
						                                                     ))
						.orElseGet(() -> new Map[] {});
				returnVal.affectBlocks(blocks, blocksById);
			}

			return returnVal;

		} catch (final ClassNotFoundException e)
		{
			throw new JsonParseException(e);
		}
	}

	public static class Factory implements StatefulSerializer.Deserializer<MaskedSource<?, ?>, MaskedSourceDeserializer>
	{

		@Override
		public MaskedSourceDeserializer createDeserializer(
				final Arguments arguments,
				final Supplier<String> projectDirectory,
				final IntFunction<SourceState<?, ?>> dependencyFromIndex)
		{
			return new MaskedSourceDeserializer(projectDirectory, arguments.propagationWorkers);
		}

	}

}
