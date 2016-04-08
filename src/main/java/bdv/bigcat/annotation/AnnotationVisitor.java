package bdv.bigcat.annotation;

public abstract class AnnotationVisitor {

	public abstract void visit(Synapse synapse);
	public abstract void visit(SynapticSite synapticSite);
}
