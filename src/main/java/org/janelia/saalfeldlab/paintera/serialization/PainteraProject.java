package org.janelia.saalfeldlab.paintera.serialization;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import bdv.viewer.Source;
import javafx.collections.ListChangeListener;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;

public class PainteraProject implements TransformListener< AffineTransform3D >
{

	private static String PROJECT_KEY = "paintera";

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static final GsonBuilder builder = new GsonBuilder()
			.setPrettyPrinting()
			.registerTypeAdapter( AffineTransform3D.class, new AffineTransform3DJsonAdapter() );

	private transient PainteraBaseView viewer;

	private final List< PainteraSource > painteraSources = new ArrayList<>();

	private transient final Map< Source< ? >, PainteraSource > sourceToPainteraSource = new HashMap<>();

	private transient final Map< PainteraSource, Source< ? > > painteraSourceToSource = new HashMap<>();

	private transient String projectDirectory;

	@Expose
	private final WindowProperties windowProperties = new WindowProperties();

	@Expose
	private final AffineTransform3D transform = new AffineTransform3D();

	private transient boolean isDirty = false;

	public void setViewer( final PainteraBaseView viewer )
	{
		this.viewer = viewer;
		this.viewer.manager().addListener( this );
		this.viewer.sourceInfo().trackSources().addListener( ( ListChangeListener< Source< ? > > ) change -> {
			while ( change.next() )
			{
				final List< ? extends Source< ? > > currentList = change.getList();
				final List< PainteraSource > painteraSources = new ArrayList<>();
				for ( final Source< ? > s : currentList )
					Optional.ofNullable( sourceToPainteraSource.get( s ) ).ifPresent( painteraSources::add );
				this.painteraSources.clear();
				this.painteraSources.addAll( painteraSources );
				stain();
			}
		} );
		this.viewer.sourceInfo().removedSourcesTracker().addListener( ( ListChangeListener< Source< ? > > ) change -> {
			while ( change.next() )
			{
				for ( final Source< ? > source : change.getList() )
				{
					final PainteraSource ps = this.sourceToPainteraSource.remove( source );
					this.painteraSourceToSource.remove( ps );
					this.painteraSources.remove( ps );
				}
			}
		} );
	}

	public boolean isDirty()
	{
		return isDirty;
	}

	private void stain()
	{
		this.isDirty = true;
	}

	private void clean()
	{
		this.isDirty = false;
	}

	@Override
	public void transformChanged( final AffineTransform3D transform )
	{
		if ( !Arrays.equals( transform.getRowPackedCopy(), this.transform.getRowPackedCopy() ) )
		{
			this.transform.set( transform );
			stain();
		}
	}

	public void setWidth( final int width )
	{
		LOG.debug( "Setting width={} from previosly {}", width, this.windowProperties.width );
		// for some reason, stage gets resized to 1 at start-up
		if ( width != this.windowProperties.width )
		{
			this.windowProperties.width = width;
			stain();
		}
	}

	public void setHeight( final int height )
	{
		LOG.debug( "Setting height={} from previosly {}", height, this.windowProperties.height );
		if ( height != this.windowProperties.height )
		{
			this.windowProperties.height = height;
			stain();
		}
	}

	public void setProjectDirectory( final String projectDirectory )
	{
		setProjectDirectory( projectDirectory, true );
	}

	public void setProjectDirectory( final String projectDirectory, final boolean stain )
	{
		if ( !Optional.ofNullable( this.projectDirectory ).map( d -> d.equals( projectDirectory ) ).orElse( false ) )
		{
			this.projectDirectory = projectDirectory;
			// TODO update paintera sources to be relative to projectDirectory
			// if
			// possible
			if ( stain )
				stain();
		}
	}

	public void addPainteraSource( final PainteraSource painteraSource )
	{
		final Optional< Source< ? > > source = painteraSource.addToViewer( viewer );
		if ( source.isPresent() )
		{
			this.painteraSources.add( painteraSource );
			this.sourceToPainteraSource.put( source.get(), painteraSource );
			stain();
		}
	}

	public void persist() throws ProjectDirectoryNotSetException, IOException
	{
		if ( this.projectDirectory == null )
			throw new ProjectDirectoryNotSetException();
		final N5Writer writer = N5Helpers.n5Writer( this.projectDirectory, builder, 64, 64, 64 );
		writer.createGroup( "" );
		writer.setAttribute( "", PROJECT_KEY, this );
		clean();
	}

	public int width()
	{
		return this.windowProperties.width;
	}

	public int height()
	{
		return this.windowProperties.height;
	}

	public AffineTransform3D globalTransformCopy()
	{
		LOG.debug( "Returning transform copy: {}", transform );
		return this.transform.copy();
	}

}
