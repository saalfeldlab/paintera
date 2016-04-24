package bdv.bigcat.annotation;

public abstract class AnnotationVisitor {
	
	public void visit(Annotation annotation) {}

	public abstract void visit(Synapse synapse);
	public abstract void visit(PreSynapticSite preSynapticSite);
	public abstract void visit(PostSynapticSite postSynapticSite);
}
