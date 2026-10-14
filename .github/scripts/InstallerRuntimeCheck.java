import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtProvider;
import org.janelia.saalfeldlab.n5.ByteArrayDataBlock;
import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.blosc.BloscCompression;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Checks the native libraries of an installed Paintera. Run with the installed app's own runtime, classpath, and
 * the `java-options` of its `Paintera.cfg`, see `verify-installer.sh`. Not part of the app.
 */
public class InstallerRuntimeCheck {

	private static final String[] BLOSC_COMPRESSORS = {"blosclz", "lz4", "lz4hc", "zlib", "zstd"};

	public static void main(final String[] args) throws Exception {

		checkBlosc();
		checkOnnxRuntime();
		System.out.println("OK: installer runtime check passed");
	}

	/* create a dataset with each blosc compressor, then write a block and read it back */
	private static void checkBlosc() throws Exception {

		final Path container = Files.createTempDirectory("paintera-blosc-check");
		try (N5Writer n5 = new N5FSWriter(container.toString())) {
			final byte[] data = new byte[8 * 8];
			for (int i = 0; i < data.length; i++)
				data[i] = (byte)(i % 7);
			for (final String compressor : BLOSC_COMPRESSORS) {
				final String dataset = "blosc-" + compressor;
				final DatasetAttributes requested = new DatasetAttributes(
						new long[]{16, 16}, new int[]{8, 8}, DataType.UINT8,
						new BloscCompression(compressor, 5, BloscCompression.SHUFFLE, 0, 1));
				n5.createDataset(dataset, requested);
				final DatasetAttributes attributes = n5.getDatasetAttributes(dataset);
				n5.writeBlock(dataset, attributes, new ByteArrayDataBlock(new int[]{8, 8}, new long[]{0, 0}, data));
				final DataBlock<byte[]> block = n5.readBlock(dataset, attributes, 0, 0);
				if (block == null || !Arrays.equals(data, block.getData()))
					throw new IllegalStateException("blosc block read back differs from the written block, compressor=" + compressor);
				System.out.println("blosc " + compressor + ": ok");
			}
		} finally {
			try (Stream<Path> paths = Files.walk(container)) {
				paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
			}
		}
	}

	/* creating the environment loads the native libraries */
	private static void checkOnnxRuntime() {

		final OrtEnvironment environment = OrtEnvironment.getEnvironment();
		final var providers = OrtEnvironment.getAvailableProviders();
		if (!providers.contains(OrtProvider.CPU))
			throw new IllegalStateException("onnxruntime has no CPU provider, available: " + providers);
		System.out.println("onnxruntime " + environment.getVersion() + ": ok, providers " + providers);
	}
}
