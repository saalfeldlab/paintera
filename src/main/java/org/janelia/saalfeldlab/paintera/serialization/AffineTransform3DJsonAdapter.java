package org.janelia.saalfeldlab.paintera.serialization;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.plugin.Plugin;

import java.lang.reflect.Type;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class AffineTransform3DJsonAdapter implements PainteraSerialization.PainteraAdapter<AffineTransform3D> {

	@Override
	public JsonElement serialize(final AffineTransform3D transform, final Type typeOfSrc, final
	JsonSerializationContext context) {

		return context.serialize(transform.getRowPackedCopy());
	}

	@Override
	public AffineTransform3D deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext
			context)
			throws JsonParseException {

		final double[] data = context.deserialize(json, double[].class);
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(data);
		return transform;
	}

	@Override
	public Class<AffineTransform3D> getTargetClass() {

		return AffineTransform3D.class;
	}
}
