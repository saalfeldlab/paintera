package org.janelia.saalfeldlab.paintera.data.n5

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions
import org.janelia.saalfeldlab.paintera.serialization.StatefulSerializer
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.scijava.plugin.Plugin
import java.lang.reflect.Type
import java.util.function.IntFunction
import java.util.function.Supplier
import java.util.function.ToIntFunction

private object FileSystemSerializationKeys {
    const val BASE_PATH = "basePath"
}

private class FileSystemSerializer<N5 : N5FSReader>(private val projectDirectory: Supplier<String>) : JsonSerializer<N5> {
    override fun serialize(
        container: N5,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        val projectDirectory = this.projectDirectory.get()
        return with(FileSystemSerializationKeys) {
            JsonObject()
                .also { m -> container.basePath.takeUnless { it == projectDirectory }?.let { m.addProperty(BASE_PATH, it) } }
        }
    }
}

private class FileSystemDeserializer<N5 : N5FSReader>(
    private val projectDirectory: Supplier<String>,
    private val n5Constructor: (String) -> N5
) : JsonDeserializer<N5> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): N5 {
        return with(GsonExtensions) {
            with(FileSystemSerializationKeys) {
                n5Constructor(json.getStringProperty(BASE_PATH) ?: projectDirectory.get())
            }
        }
    }
}

@Plugin(type = StatefulSerializer.SerializerAndDeserializer::class)
class N5FSReaderAdapter : StatefulSerializer.SerializerAndDeserializer<N5FSReader, JsonDeserializer<N5FSReader>, JsonSerializer<N5FSReader>> {

    override fun createSerializer(
        projectDirectory: Supplier<String>,
        stateToIndex: ToIntFunction<SourceState<*, *>>
    ): JsonSerializer<N5FSReader> = FileSystemSerializer(projectDirectory)

    override fun createDeserializer(
        arguments: StatefulSerializer.Arguments,
        projectDirectory: Supplier<String>,
        dependencyFromIndex: IntFunction<SourceState<*, *>>?
    ): JsonDeserializer<N5FSReader> = FileSystemDeserializer(projectDirectory) { N5FSReader(it) }

    override fun getTargetClass() = N5FSReader::class.java
}

@Plugin(type = StatefulSerializer.SerializerAndDeserializer::class)
class N5FSWriterAdapter : StatefulSerializer.SerializerAndDeserializer<N5FSWriter, JsonDeserializer<N5FSWriter>, JsonSerializer<N5FSWriter>> {

    override fun createSerializer(
        projectDirectory: Supplier<String>,
        stateToIndex: ToIntFunction<SourceState<*, *>>
    ): JsonSerializer<N5FSWriter> = FileSystemSerializer(projectDirectory)

    override fun createDeserializer(
        arguments: StatefulSerializer.Arguments,
        projectDirectory: Supplier<String>,
        dependencyFromIndex: IntFunction<SourceState<*, *>>?
    ): JsonDeserializer<N5FSWriter> = FileSystemDeserializer(projectDirectory) { N5FSWriter(it) }

    override fun getTargetClass() = N5FSWriter::class.java
}
