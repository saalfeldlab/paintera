package org.janelia.saalfeldlab.paintera.serialization.converter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import gnu.trove.iterator.TLongIntIterator;
import gnu.trove.map.TLongIntMap;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.util.Colors;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Optional;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class HighlightingStreamConverterSerializer implements PainteraSerialization.PainteraAdapter<HighlightingStreamConverter<?>> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String TYPE_KEY = "converterType";

	public static final String STREAM_TYPE_KEY = "streamType";

	public static final String SPECIFIED_COLORS_KEY = "userSpecifiedColors";

	public static final String SEED_KEY = "seed";

	@Override
	public HighlightingStreamConverter<?> deserialize(final JsonElement json, final Type typeOfT, final
	JsonDeserializationContext context)
			throws JsonParseException {

		try {
			final JsonObject map = json.getAsJsonObject();
			LOG.debug("Deserializing from map {}", map);

			@SuppressWarnings("unchecked") final var streamClass = (Class<? extends AbstractHighlightingARGBStream>)Class.forName(map.get(STREAM_TYPE_KEY)
							.getAsString());
			final AbstractHighlightingARGBStream stream = streamClass.getConstructor().newInstance();

			if (map.has(SPECIFIED_COLORS_KEY)) {
				final JsonObject colorsMap = map.get(SPECIFIED_COLORS_KEY).getAsJsonObject();
				for (final Iterator<Entry<String, JsonElement>> it = colorsMap.entrySet().iterator(); it.hasNext(); ) {
					final Entry<String, JsonElement> entry = it.next();
					stream.specifyColorExplicitly(
							Long.parseLong(entry.getKey()),
							Colors.toARGBType(entry.getValue().getAsString()).get()
					);
				}
			}

			@SuppressWarnings("unchecked") final Class<? extends HighlightingStreamConverter<?>> converterClass =
					(Class<? extends HighlightingStreamConverter<?>>)Class.forName(map.get(TYPE_KEY).getAsString());
			LOG.debug("Converter class is {}", converterClass);
			final HighlightingStreamConverter<?> converter = converterClass.getConstructor(
					AbstractHighlightingARGBStream.class).newInstance(stream);
			Optional.ofNullable(map.get(SEED_KEY)).map(JsonElement::getAsLong).ifPresent(converter.seedProperty()
					::set);
			LOG.debug("Returning converter {}", converter);
			return converter;
		} catch (InstantiationException
				 | IllegalAccessException
				 | IllegalArgumentException
				 | InvocationTargetException
				 | NoSuchMethodException
				 | SecurityException
				 | ClassNotFoundException e) {
			throw new JsonParseException(e);
		}
	}

	@Override
	public JsonElement serialize(
			final HighlightingStreamConverter<?> src,
			final Type typeOfSrc,
			final JsonSerializationContext context) {

		final JsonObject map = new JsonObject();
		final AbstractHighlightingARGBStream stream = src.getStream();
		map.addProperty(TYPE_KEY, src.getClass().getName());
		map.addProperty(STREAM_TYPE_KEY, stream.getClass().getName());
		map.addProperty(SEED_KEY, stream.getSeed());

		final TLongIntMap specifiedColors = stream.getExplicitlySpecifiedColorsCopy();
		if (specifiedColors.size() > 0) {
			final JsonObject colors = new JsonObject();
			final ARGBType dummy = new ARGBType();
			for (final TLongIntIterator colorIt = specifiedColors.iterator(); colorIt.hasNext(); ) {
				colorIt.advance();
				dummy.set(colorIt.value());
				colors.addProperty(Long.toString(colorIt.key()), Colors.toHTML(dummy));
			}
			LOG.debug("Adding specified colors {}", colors);
			map.add(SPECIFIED_COLORS_KEY, colors);

		}

		LOG.debug("Returning serialized converter as {}", map);

		return map;
	}

	@Override
	public boolean isHierarchyAdapter() {

		return true;
	}

	@Override
	public Class<HighlightingStreamConverter<?>> getTargetClass() {

		return (Class<HighlightingStreamConverter<?>>)(Class<?>)HighlightingStreamConverter.class;
	}
}
