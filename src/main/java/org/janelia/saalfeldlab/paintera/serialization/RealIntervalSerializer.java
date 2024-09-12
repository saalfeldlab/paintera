package org.janelia.saalfeldlab.paintera.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import jnr.ffi.annotations.In;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import org.scijava.plugin.Plugin;

import java.lang.reflect.Type;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class RealIntervalSerializer implements PainteraSerialization.PainteraAdapter<RealInterval> {

	@Override
	public RealInterval deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
			throws JsonParseException {

		final JsonObject intervalObj = json.getAsJsonObject();
		final JsonArray min = intervalObj.get("min").getAsJsonArray();
		final JsonArray max = intervalObj.get("max").getAsJsonArray();
		try {
			return deserializeToInterval(min, max);
		} catch (NumberFormatException e) {
			return deserializeToRealInterval(min, max);
		}
	}

	private Interval deserializeToInterval(JsonArray min, JsonArray max) {

		final int size = min.size();
		final long[] intervalMin = new long[size];
		final long[] intervalMax = new long[size];

		for (int i = 0; i < size; i++) {
			intervalMin[i] = min.get(i).getAsLong();
			intervalMax[i] = max.get(i).getAsLong();
		}
		return new FinalInterval(intervalMin, intervalMax);
	}

	private RealInterval deserializeToRealInterval(JsonArray min, JsonArray max) {

		final int size = min.size();
		final double[] intervalMin = new double[size];
		final double[] intervalMax = new double[size];

		for (int i = 0; i < size; i++) {
			intervalMin[i] = min.get(i).getAsDouble();
			intervalMax[i] = max.get(i).getAsDouble();
		}
		return new FinalRealInterval(intervalMin, intervalMax);
	}

	@Override
	public JsonElement serialize(final RealInterval src, final Type typeOfSrc, final JsonSerializationContext context) {

		final JsonObject interval = new JsonObject();
		if (src instanceof Interval) {
			interval.add("min", context.serialize(((Interval)src).minAsLongArray()));
			interval.add("max", context.serialize(((Interval)src).maxAsLongArray()));
		} else {
			interval.add("min", context.serialize(src.minAsDoubleArray()));
			interval.add("max", context.serialize(src.maxAsDoubleArray()));
		}
		return interval;
	}

	@Override
	public Class<RealInterval> getTargetClass() {

		return RealInterval.class;
	}

	@Override
	public boolean isHierarchyAdapter() {

		return true;
	}
}
