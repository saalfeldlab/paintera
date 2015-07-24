package bdv.util.dvid;

public class Dataset
{
	
	public static final String PROPERTY_TYPENAME = "typename";
	public static final String POPERTY_DATANAME = "dataname";
	public static final String PROPERTY_SYNC = "sync";
	
	private final String uuid;
	private final String name;
	private final String type;
	public String getUuid()
	{
		return uuid;
	}


	public String getName()
	{
		return name;
	}


	public String getType()
	{
		return type;
	}


	public String[] getSync()
	{
		return sync;
	}


	private final String[] sync;
	
	
	public Dataset( String uuid, String name, String type, String... sync )
	{
		super();
		this.uuid = uuid;
		this.name = name;
		this.type = type;
		this.sync = sync;
	}
	
	

}
