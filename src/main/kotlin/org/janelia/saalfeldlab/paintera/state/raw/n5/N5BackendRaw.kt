package org.janelia.saalfeldlab.paintera.state.raw.n5

import bdv.util.volatiles.SharedQueue
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5Meta
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers
import org.janelia.saalfeldlab.util.n5.N5Helpers
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

// NB: If this ever becomes dataset dependent, we should create individual classes for
//         - dataset
//         - multi-scale group
//         - paintera dataset

class N5BackendRaw<D, T> constructor(override val container: N5Reader, override val dataset: String) : AbstractN5BackendRaw<D, T>
    where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {

    override fun createSource(
        queue: SharedQueue,
        priority: Int,
        name: String,
        resolution: DoubleArray,
        offset: DoubleArray
    ): DataSource<D, T> {
        return N5DataSource(
            N5Meta.fromReader(container, dataset),
            N5Helpers.fromResolutionAndOffset(resolution, offset),
            name,
            queue,
            priority
        )
    }

    private object SerializationKeys {
        const val CONTAINER = "container"
        const val DATASET = "dataset"
    }

    @Plugin(type = PainteraSerialization.PainteraSerializer::class)
    class Serializer<D, T> : PainteraSerialization.PainteraSerializer<N5BackendRaw<D, T>>
        where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {

        override fun serialize(
            backend: N5BackendRaw<D, T>,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            val map = JsonObject()
            with(SerializationKeys) {
                //TODO I suspect serialization should not be possible if read-only.
                map.add(CONTAINER, SerializationHelpers.serializeWithClassInfo(backend.container, context))
                map.addProperty(DATASET, backend.dataset)
            }
            return map
        }

        override fun getTargetClass() = N5BackendRaw::class.java as Class<N5BackendRaw<D, T>>
    }

    @Plugin(type = PainteraSerialization.PainteraDeserializer::class)
    class Deserializer<D, T>() : PainteraSerialization.PainteraDeserializer<N5BackendRaw<D, T>>
        where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): N5BackendRaw<D, T> {
            return with(SerializationKeys) {
                with(GsonExtensions) {
                    N5BackendRaw<D, T>(
                        SerializationHelpers.deserializeFromClassInfo(json.getJsonObject(CONTAINER)!!, context),
                        json.getStringProperty(DATASET)!!
                    )
                }
            }
        }

        override fun getTargetClass() = N5BackendRaw::class.java as Class<N5BackendRaw<D, T>>
    }
}
