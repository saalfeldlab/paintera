package bdv.bigcat.viewer;

import java.util.ArrayList;
import java.util.List;

import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class BaseViewState
{

	protected final GridConstraintsManager constraintsManager;

	protected final GlobalTransformManager globalTransform;

	protected final ViewerOptions viewerOptions;

	protected final ArrayList< Converter< ?, ARGBType > > converters;

	public BaseViewState()
	{
		this( ViewerOptions.options() );
	}

	public BaseViewState( final ViewerOptions viewerOptions )
	{
		this( viewerOptions, new GlobalTransformManager(), new GridConstraintsManager(), new ArrayList<>() );
	}

	public BaseViewState( final ViewerOptions viewerOptions, final GlobalTransformManager globalTransform, final GridConstraintsManager constraintsManager, final List< Converter< ?, ARGBType > > converters )
	{
		this.viewerOptions = viewerOptions;
		this.globalTransform = globalTransform;
		this.constraintsManager = constraintsManager;
		this.converters = new ArrayList<>();
		this.converters.addAll( converters );
	}

	protected void trackConverters( final ObservableList< SourceAndConverter< ? > > list )
	{
		list.addListener( new UpdateConverters() );
	}

	private class UpdateConverters implements ListChangeListener< SourceAndConverter< ? > >
	{

		@Override
		public void onChanged( final Change< ? extends SourceAndConverter< ? > > c )
		{
			while ( c.next() )
			{
				converters.clear();
				c.getList().stream().map( SourceAndConverter::getConverter ).forEach( converters::add );
			}
		}

	}

}
