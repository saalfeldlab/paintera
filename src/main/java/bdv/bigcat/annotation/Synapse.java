package bdv.bigcat.annotation;

import java.util.LinkedList;
import java.util.List;

import net.imglib2.RealPoint;

public class Synapse extends Annotation {

	public Synapse(long id, RealPoint pos, String comment) {
		super(id, pos, comment);
	}

	public PostSynapticSite getPreSynapticPartner() {
		return preSynapticPartner;
	}

	public void setPreSynapticPartner(PostSynapticSite preSynapticPartner) {
		this.preSynapticPartner = preSynapticPartner;
	}

	public List<PostSynapticSite> getPostSynapticPartners() {
		return postSynapticPartners;
	}

	public void setPostSynapticPartners(List<PostSynapticSite> postSynapticPartners) {
		this.postSynapticPartners = postSynapticPartners;
	}

	public void addPostSynapticPartner(PostSynapticSite site) {
		this.postSynapticPartners.add(site);
		
	}

	@Override
	public void accept(AnnotationVisitor visitor) {
		visitor.visit(this);
	}
	
	private PostSynapticSite preSynapticPartner;
	private List< PostSynapticSite > postSynapticPartners = new LinkedList< PostSynapticSite >();
}