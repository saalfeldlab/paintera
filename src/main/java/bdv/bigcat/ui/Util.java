/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package bdv.bigcat.ui;

import static bdv.bigcat.CombinedImgLoader.SetupIdAndLoader.setupIdAndLoader;

import java.awt.Font;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.BigDataViewer;
import bdv.ViewerSetupImgLoader;
import bdv.bigcat.CombinedImgLoader;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeProjector;
import bdv.img.SetCache;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.RealARGBColorConverterSetup;
import bdv.viewer.DisplayMode;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.render.AccumulateProjectorFactory;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import net.imglib2.display.ScaledARGBConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.volatiles.VolatileARGBType;

/**
 * UI related static utility methods
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class Util
{
	private Util(){}

	public static void initUI()
	{
		try
		{
			UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
			Util.checkGTKLookAndFeel();
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}

		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
	}

	/**
	 * Look and feel improvement on Ubuntu
	 *
	 * http://stackoverflow.com/questions/3907047/how-to-change-the-default-font
	 * -size-in-the-swing-gtk-lookandfeel/31345102#31345102
	 *
	 * @throws Exception
	 */
	public static void checkGTKLookAndFeel() throws Exception
	{
		final LookAndFeel look = UIManager.getLookAndFeel();
		if ( !look.getID().equals( "GTK" ) )
			return;

		new JFrame();
		new JButton();
		new JComboBox<>();
		new JRadioButton();
		new JCheckBox();
		new JTextArea();
		new JTextField();
		new JTable();
		new JToggleButton();
		new JSpinner();
		new JSlider();
		new JTabbedPane();
		new JMenu();
		new JMenuBar();
		new JMenuItem();

		Object styleFactory;
		final Field styleFactoryField = look.getClass().getDeclaredField( "styleFactory" );
		styleFactoryField.setAccessible( true );
		styleFactory = styleFactoryField.get( look );

		final Field defaultFontField = styleFactory.getClass().getDeclaredField( "defaultFont" );
		defaultFontField.setAccessible( true );
		final Font defaultFont = ( Font ) defaultFontField.get( styleFactory );
		FontUIResource newFontUI;
		newFontUI = new FontUIResource( defaultFont.deriveFont( defaultFont.getSize() - 2f ) );
		defaultFontField.set( styleFactory, newFontUI );

		final Field stylesCacheField = styleFactory.getClass().getDeclaredField( "stylesCache" );
		stylesCacheField.setAccessible( true );
		final Object stylesCache = stylesCacheField.get( styleFactory );
		final Map stylesMap = ( Map ) stylesCache;
		for ( final Object mo : stylesMap.values() )
		{
			final Field f = mo.getClass().getDeclaredField( "font" );
			f.setAccessible( true );
			final Font fo = ( Font ) f.get( mo );
			f.set( mo, fo.deriveFont( fo.getSize() - 2f ) );
		}
	}

	public static < A extends ViewerSetupImgLoader< ? extends NumericType< ? >, ? > & SetCache > BigDataViewer createViewer(
			final String windowTitle,
			final A[] rawDataLoaders,
			final AbstractARGBConvertedLabelsSource[] labelSources,
			final SetCache[] labelLoaders,
			final List< Composite< ARGBType, ARGBType > > composites,
			final InputTriggerConfig config)
	{
		/* raw pixels */
		final CombinedImgLoader.SetupIdAndLoader[] loaders = new CombinedImgLoader.SetupIdAndLoader[ rawDataLoaders.length ];
		int setupId = 0;
		for ( int i = 0; i < rawDataLoaders.length; ++i )
			loaders[ i ] = setupIdAndLoader( setupId++, rawDataLoaders[ i ] );

		final CombinedImgLoader imgLoader = new CombinedImgLoader( loaders );
		for ( int i = 0; i < rawDataLoaders.length; ++i )
			rawDataLoaders[ i ].setCache( imgLoader.getCacheControl() );

		final ArrayList< TimePoint > timePointsList = new ArrayList< >();
		final Map< Integer, BasicViewSetup > setups = new HashMap< >();
		final ArrayList< ViewRegistration > viewRegistrationsList = new ArrayList< >();
		for ( final CombinedImgLoader.SetupIdAndLoader loader : loaders )
		{
			timePointsList.add( new TimePoint( 0 ) );
			setups.put( loader.setupId, new BasicViewSetup( loader.setupId, null, null, null ) );
			viewRegistrationsList.add( new ViewRegistration( 0, loader.setupId ) );
		}

		final TimePoints timepoints = new TimePoints( timePointsList );
		final ViewRegistrations reg = new ViewRegistrations( viewRegistrationsList );
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( timepoints, setups, imgLoader, null );
		final SpimDataMinimal spimData = new SpimDataMinimal( null, seq, reg );

		final ArrayList< ConverterSetup > converterSetups = new ArrayList< ConverterSetup >();
		final ArrayList< SourceAndConverter< ? > > sources = new ArrayList< SourceAndConverter< ? > >();

		BigDataViewer.initSetups( spimData, converterSetups, sources );

		/* labels */
		for ( final SetCache setCache : labelLoaders )
			setCache.setCache( imgLoader.getCacheControl() );

		for ( final AbstractARGBConvertedLabelsSource source : labelSources )
		{
			final ScaledARGBConverter.ARGB converter = new ScaledARGBConverter.ARGB( 0, 255 );
			final ScaledARGBConverter.VolatileARGB vconverter = new ScaledARGBConverter.VolatileARGB( 0, 255 );

			final SourceAndConverter< VolatileARGBType > vsoc = new SourceAndConverter< VolatileARGBType >( source, vconverter );
			@SuppressWarnings( "unchecked" )
			final SourceAndConverter< ARGBType > soc = new SourceAndConverter< ARGBType >( source.nonVolatile(), converter, vsoc );
			sources.add( soc );

			final RealARGBColorConverterSetup fragmentsConverterSetup = new RealARGBColorConverterSetup( 2, converter, vconverter );
			converterSetups.add( fragmentsConverterSetup );
		}

		/* composites */
		final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositesMap = new HashMap< Source< ? >, Composite< ARGBType, ARGBType > >();
		for ( int i = 0; i < composites.size(); ++i )
			sourceCompositesMap.put( sources.get( i ).getSpimSource(), composites.get( i ) );

		final AccumulateProjectorFactory< ARGBType > projectorFactory = new CompositeProjector.CompositeProjectorFactory< ARGBType >( sourceCompositesMap );

		ViewerOptions options = ViewerOptions.options()
				.accumulateProjectorFactory(projectorFactory)
				.numRenderingThreads(16)
				.targetRenderNanos(10000000);
		if (config != null)
			options = options.inputTriggerConfig(config);
		
		options = options.screenScales( new double[] { 1, 0.5, 0.25, 0.125 } );

		final BigDataViewer bdv = new BigDataViewer( converterSetups, sources, null, timepoints.size(), imgLoader.getCacheControl(), windowTitle, null, options );

		final AffineTransform3D transform = new AffineTransform3D();
		bdv.getViewer().setCurrentViewerTransform( transform );
		bdv.getViewer().setDisplayMode( DisplayMode.FUSED );

		/* separate source min max */
		for ( final ConverterSetup converterSetup : converterSetups )
		{
			bdv.getSetupAssignments().removeSetupFromGroup( converterSetup, bdv.getSetupAssignments().getMinMaxGroups().get( 0 ) );
			converterSetup.setDisplayRange( 0, 255 );
		}

		return bdv;
	}
}
