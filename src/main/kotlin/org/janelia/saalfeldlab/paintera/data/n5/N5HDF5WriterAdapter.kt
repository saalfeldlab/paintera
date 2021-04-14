package org.janelia.saalfeldlab.paintera.data.n5

import com.google.gson.*
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.util.n5.universe.N5Factory
import org.scijava.plugin.Plugin
import java.lang.reflect.Type
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.IntFunction
import java.util.function.Supplier
import java.util.function.ToIntFunction

private object HDF5SerializationKeys {
    const val FILE = "file"
    const val DEFAULT_BLOCK_SIZE = "defaultBlockSize"
    const val OVERRIDE_BLOCK_SIZE = "overrideBlockSize"
    const val IS_RELATIVE_TO_PROJECT = "isRelativeToProject"

    fun JsonElement.isRelative() = with(GsonExtensions) { getBooleanProperty(IS_RELATIVE_TO_PROJECT) } ?: false

    fun Path.realAbsolute() = toRealPath().toAbsolutePath()

    fun String.isContainedIn(parent: Path) = Paths.get(this).isContainedIn(parent)

    fun Path.isContainedIn(parent: Path) = this.realAbsolute().startsWith(parent.realAbsolute())

    fun Path.relativize(other: String) = this.relativize(Paths.get(other))

    fun String.resolve(other: String) = Paths.get(this).resolve(other).toString()
}

private class HDF5Serializer<N5 : N5HDF5Reader>(private val projectDirectory: Supplier<String>) : JsonSerializer<N5> {
    override fun serialize(
        container: N5,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        val projectDirectory = Paths.get(this.projectDirectory.get()).toAbsolutePath()
        val file = container.filename.absolutePath
        return with(HDF5SerializationKeys) {
            val isRelative = file.isContainedIn(projectDirectory)
            JsonObject()
                .also { m -> container.defaultBlockSizeCopy?.takeIf { it.isNotEmpty() }?.let { m.add(DEFAULT_BLOCK_SIZE, context.serialize(it)) } }
                .also { it.addProperty(OVERRIDE_BLOCK_SIZE, container.doesOverrideBlockSize()) }
                .also { it.takeIf { isRelative }?.addProperty(IS_RELATIVE_TO_PROJECT, isRelative) }
                .also { it.addProperty(FILE, if (isRelative) projectDirectory.relativize(file).toString() else file) }
        }
    }
}

private class HDF5Deserializer<N5 : N5HDF5Reader>(
    private val projectDirectory: Supplier<String>,
    private val n5Constructor: (String, Boolean, IntArray?) -> N5
) : JsonDeserializer<N5> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): N5 {
        return with(GsonExtensions) {
            with(HDF5SerializationKeys) {
                n5Constructor(
                    json.getStringProperty(FILE)!!.let { if (json.isRelative()) projectDirectory.get().resolve(it) else it },
                    json.getBooleanProperty(OVERRIDE_BLOCK_SIZE)!!,
                    context.deserialize(json.getJsonObject(DEFAULT_BLOCK_SIZE), IntArray::class.java)
                )
            }
        }
    }
}

@Plugin(type = StatefulSerializer.SerializerAndDeserializer::class)
class N5HDF5ReaderAdapter : StatefulSerializer.SerializerAndDeserializer<N5HDF5Reader, JsonDeserializer<N5HDF5Reader>, JsonSerializer<N5HDF5Reader>> {

    override fun createSerializer(
        projectDirectory: Supplier<String>,
        stateToIndex: ToIntFunction<SourceState<*, *>>
    ): JsonSerializer<N5HDF5Reader> = HDF5Serializer(projectDirectory)

    override fun createDeserializer(
        arguments: StatefulSerializer.Arguments,
        projectDirectory: Supplier<String>,
        dependencyFromIndex: IntFunction<SourceState<*, *>>?
    ): JsonDeserializer<N5HDF5Reader> = HDF5Deserializer(projectDirectory) { file, overrideBlockSize, defaultBlockSize ->
        N5HDF5Reader(file, overrideBlockSize, *(defaultBlockSize ?: intArrayOf()))
    }

    override fun getTargetClass() = N5HDF5Reader::class.java
}

@Plugin(type = StatefulSerializer.SerializerAndDeserializer::class)
class N5HDF5WriterAdapter : StatefulSerializer.SerializerAndDeserializer<N5HDF5Writer, JsonDeserializer<N5HDF5Writer>, JsonSerializer<N5HDF5Writer>> {

    override fun createSerializer(
        projectDirectory: Supplier<String>,
        stateToIndex: ToIntFunction<SourceState<*, *>>
    ): JsonSerializer<N5HDF5Writer> = HDF5Serializer(projectDirectory)

    override fun createDeserializer(
        arguments: StatefulSerializer.Arguments,
        projectDirectory: Supplier<String>,
        dependencyFromIndex: IntFunction<SourceState<*, *>>?
    ): JsonDeserializer<N5HDF5Writer> = HDF5Deserializer(projectDirectory) { file, _, defaultBlockSize ->
        val factory = N5Factory()
        factory.hdf5DefaultBlockSize(*(defaultBlockSize ?: intArrayOf()))
        //FIXME this should be temporary! we should generify these special adaptors if possible.
        factory.openWriter(file) as N5HDF5Writer
    }

    override fun getTargetClass() = N5HDF5Writer::class.java
}
