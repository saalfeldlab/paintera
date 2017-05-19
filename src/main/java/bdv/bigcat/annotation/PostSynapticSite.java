package bdv.bigcat.annotation;

import net.imglib2.RealPoint;

public class PostSynapticSite extends Annotation
{
	private PreSynapticSite partner;

	public PostSynapticSite( long id, RealPoint pos, String comment )
	{
		super( id, pos, comment );
	}

	public PreSynapticSite getPartner()
	{
		return partner;
	}

	public void setPartner( PreSynapticSite partner )
	{
		this.partner = partner;
	}

	@Override
	public void accept( AnnotationVisitor visitor )
	{
		super.accept( visitor );
		visitor.visit( this );
	}
}
