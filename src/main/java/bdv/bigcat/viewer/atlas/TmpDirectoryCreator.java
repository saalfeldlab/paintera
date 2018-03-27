package bdv.bigcat.viewer.atlas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.function.Supplier;

public class TmpDirectoryCreator implements Supplier< String >
{

	private final Path dir;

	private final String prefix;

	private final FileAttribute< ? >[] attrs;

	public TmpDirectoryCreator( final Path dir, final String prefix, final FileAttribute< ? >... attrs )
	{
		super();
		this.dir = dir;
		this.prefix = prefix;
		this.attrs = attrs;
	}

	@Override
	public String get()
	{
		try
		{
			return dir == null ? Files.createTempDirectory( prefix, attrs ).toString() : Files.createTempDirectory( dir, prefix, attrs ).toString();
		}
		catch ( final IOException e )
		{
			throw new RuntimeException( e );
		}
	}

}
