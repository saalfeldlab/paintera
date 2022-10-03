package org.janelia.saalfeldlab.paintera.state.raw.n5

import bdv.util.volatiles.SharedQueue
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.RealType
import net.imglib2.type.volatiles.AbstractVolatileRealType
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSourceMetadata
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.Companion.get
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.fromClassInfo
import org.janelia.saalfeldlab.paintera.serialization.SerializationHelpers.withClassInfo
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5Utils.urlRepresentation
import org.scijava.plugin.Plugin
import java.lang.reflect.Type

class N5BackendRaw<D, T> constructor(@JvmField val metadataState: MetadataState) : AbstractN5BackendRaw<D, T>
    where D : NativeType<D>, D : RealType<D>, T : AbstractVolatileRealType<D, T>, T : NativeType<T> {

    override val container = metadataState.writer ?: metadataState.reader
    override val dataset = metadataState.dataset

    override fun createSource(queue: SharedQueue, priority: Int, name: String): DataSource<D, T> {
        return N5DataSourceMetadata(metadataState, name, queue, priority)
    }

    override fun getMetadataState(): MetadataState {
        return metadataState
    }
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
            map.add(CONTAINER, context.withClassInfo(backend.container))
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
            val container: N5Reader = context.fromClassInfo(json, CONTAINER)!!
            val dataset: String = json[DATASET]!!
            val n5ContainerState = N5ContainerState(container.urlRepresentation(), container, container as? N5Writer)
            val metadataState = MetadataUtils.createMetadataState(n5ContainerState, dataset).nullable!!
            N5BackendRaw(metadataState)
        }
    }

    override fun getTargetClass() = N5BackendRaw::class.java as Class<N5BackendRaw<D, T>>
}
