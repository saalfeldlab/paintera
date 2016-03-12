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

import java.awt.Font;
import java.lang.reflect.Field;
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
		newFontUI = new FontUIResource( defaultFont.deriveFont( ( float )( defaultFont.getSize() - 2f ) ) );
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
			f.set( mo, fo.deriveFont( ( float ) ( fo.getSize() - 2f ) ) );
		}
	}

}
