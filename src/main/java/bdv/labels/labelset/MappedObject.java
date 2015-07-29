package bdv.labels.labelset;

public abstract class MappedObject< O extends MappedObject< O, T >, T extends MappedAccess< T > >
{
	protected final T access;

	protected final MappedAccessData.Factory< ? extends MappedAccessData< T >, T > storageFactory;

	@Override
	public boolean equals( final Object obj )
	{
		return obj instanceof MappedObject< ?, ? > &&
				access.equals( ( ( MappedObject< ?, ? > ) obj ).access );
	}

	@Override
	public int hashCode()
	{
		return access.hashCode();
	}

	public abstract int getSizeInBytes();

	protected MappedObject(
			final T access,
			final MappedAccessData.Factory< ? extends MappedAccessData< T >, T > storageFactory )
	{
		this.access = access;
		this.storageFactory = storageFactory;
	}

	protected void set( final O obj )
	{
		access.copyFrom( obj.access, getSizeInBytes() );
	}

	protected abstract O createRef();
}