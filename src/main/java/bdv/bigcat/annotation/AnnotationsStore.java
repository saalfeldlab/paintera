package bdv.bigcat.annotation;

public interface AnnotationsStore {

	public Annotations read();
	
	public void write(Annotations annotations);
}
