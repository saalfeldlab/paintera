package bdv.bigcat.annotation;

import net.imglib2.RealPoint;

public class SynapticSite extends Annotation {

	public SynapticSite(long id, RealPoint pos, String comment) {
		super(id, pos, comment);
	}

	public Synapse getSynapse() {
		return synapse;
	}

	public void setSynapse(Synapse synapse) {
		this.synapse = synapse;
	}

	@Override
	public void accept(AnnotationVisitor visitor) {
		visitor.visit(this);	
	}
	
	private Synapse synapse;
}