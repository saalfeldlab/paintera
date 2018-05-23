package tmp.net.imglib2.cache;

public interface FileIO< A >
{

	public A fromFile( String filename ) throws Exception;

	public void toFile( String filename, A value );

}
