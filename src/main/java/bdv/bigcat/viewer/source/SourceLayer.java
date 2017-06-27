package bdv.bigcat.viewer.source;

import bdv.viewer.SourceAndConverter;

public interface SourceLayer
{

	public SourceAndConverter< ? > getSourceAndConverter();

	public boolean isActive();

	public void setActive( boolean active );

	public String name();

	public Source< ?, ? > source();

}
