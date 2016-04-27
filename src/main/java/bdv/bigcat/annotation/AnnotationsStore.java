package bdv.bigcat.annotation;

public interface AnnotationsStore {

	public Annotations read() throws Exception;
	
	public void write(Annotations annotations);
}
