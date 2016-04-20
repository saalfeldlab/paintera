package bdv.bigcat.annotation;

import java.util.LinkedList;
import java.util.List;

import net.imglib2.RealPoint;

public class Synapse extends Annotation {

	public Synapse(long id, RealPoint pos, String comment) {
		super(id, pos, comment);
	}

	public SynapticSite getPreSynapticPartner() {
		return preSynapticPartner;
	}

	public void setPreSynapticPartner(SynapticSite preSynapticPartner) {
		this.preSynapticPartner = preSynapticPartner;
	}

	public List<SynapticSite> getPostSynapticPartners() {
		return postSynapticPartners;
	}

	public void setPostSynapticPartners(List<SynapticSite> postSynapticPartners) {
		this.postSynapticPartners = postSynapticPartners;
	}

	public void addPostSynapticPartner(SynapticSite site) {
		this.postSynapticPartners.add(site);
		
	}

	@Override
	public void accept(AnnotationVisitor visitor) {
		visitor.visit(this);
	}
	
	private SynapticSite preSynapticPartner;
	private List< SynapticSite > postSynapticPartners = new LinkedList< SynapticSite >();
}