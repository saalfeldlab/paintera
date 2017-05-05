package bdv.bigcat.annotation;

import net.imglib2.RealPoint;

public class Synapse extends Annotation {

	public Synapse(long id, RealPoint pos, String comment) {
		super(id, pos, comment);
	}

	@Override
	public void accept(AnnotationVisitor visitor) {
		super.accept(visitor);
		visitor.visit(this);
	}	
}