package bdv.bigcat.annotation;

import net.imglib2.RealPoint;

public abstract class Annotation {

	private long id;
	private RealPoint pos;
	private String comment;

	public Annotation(long id, RealPoint pos, String comment) {
		this.id = id;
		this.pos = pos;
		this.comment = comment;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public RealPoint getPosition() {
		return pos;
	}

	public void setPosition(RealPoint pos) {
		this.pos = pos;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public void accept(AnnotationVisitor visitor) {
		visitor.visit(this);
	}
}
