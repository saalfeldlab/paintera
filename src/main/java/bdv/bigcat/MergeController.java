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
package bdv.bigcat;

import java.awt.event.ActionEvent;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bdv.bigcat.ui.AbstractSaturatedARGBStream;
import bdv.labels.labelset.Multiset.Entry;
import bdv.labels.labelset.SuperVoxel;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.AbstractNamedAction;
import bdv.util.AbstractNamedAction.NamedActionAdder;
import bdv.viewer.InputActionBindings;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;

/**
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 * @author Jan Funke <jfunke@iri.upc.edu>
 */
public class MergeController
{
	final protected ViewerPanel viewer;
	final protected RealRandomAccessible< VolatileLabelMultisetType > labels;
	final protected RealRandomAccess< VolatileLabelMultisetType > labelAccess;
	final protected AbstractSaturatedARGBStream colorStream;
	final protected FragmentSegmentAssignment assignment;
	protected long activeFragmentId = 0;

	// for behavioUrs
	private final BehaviourMap behaviourMap = new BehaviourMap();
	private final InputTriggerMap inputTriggerMap = new InputTriggerMap();
	private final InputTriggerAdder inputAdder;

	// for keystroke actions
	private final ActionMap ksActionMap = new ActionMap();
	private final InputMap ksInputMap = new InputMap();
	private final NamedActionAdder ksActionAdder = new NamedActionAdder( ksActionMap );
	private final KeyStrokeAdder ksKeyStrokeAdder;

	public BehaviourMap getBehaviourMap()
	{
		return behaviourMap;
	}
	
	public InputTriggerMap getInputTriggerMap()
	{
		return inputTriggerMap;
	}

	final GsonBuilder gsonBuilder = new GsonBuilder();
	{
		gsonBuilder.registerTypeAdapter( FragmentSegmentAssignment.class, new FragmentSegmentAssignment.FragmentSegmentSerializer() );
		//gsonBuilder.setPrettyPrinting();
	}
	final Gson gson = gsonBuilder.create();

	public MergeController(
			final ViewerPanel viewer,
			final RealRandomAccessible< VolatileLabelMultisetType > labels,
			final AbstractSaturatedARGBStream colorStream,
			final FragmentSegmentAssignment assignment,
			final InputTriggerConfig config,
			final InputActionBindings inputActionBindings,
			final KeyStrokeAdder.Factory keyProperties)
	{
		this.viewer = viewer;
		this.labels = labels;
		this.colorStream = colorStream;
		this.assignment = assignment;
		labelAccess = labels.realRandomAccess();
		inputAdder = config.inputTriggerAdder( inputTriggerMap, "bigcat" );

		ksKeyStrokeAdder = keyProperties.keyStrokeAdder( ksInputMap, "bdv" );
		
		new SelectFragment("select fragment", "button1").register();
		new MergeFragment("merge fragment", "shift button1").register();
		new DetachFragment("detach fragment", "control button1").register();
		new ExportAssignments("export assignments", "E").register();
		
		inputActionBindings.addActionMap( "bdv", ksActionMap );
		inputActionBindings.addInputMap( "bdv", ksInputMap );
	}

	/**
	 * Find the id of the fragment that overlaps the most with the given pixel.
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	private long getFragmentIdByDisplayCoordinate( int x, int y )
	{
		labelAccess.setPosition( x, 0 );
		labelAccess.setPosition( y, 1 );
		labelAccess.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( labelAccess );

		final VolatileLabelMultisetType labelValues = labelAccess.get();

		// find the fragment id that has the most overlap with the visible pixel
		long fragmentId = 0;
		if ( labelValues.isValid() )
		{
			long maxCount = 0;
			for ( final Entry< SuperVoxel > entry : labelValues.get().entrySet() )
			{
				final SuperVoxel label = entry.getElement();
				final long count = entry.getCount();

				if ( count > maxCount )
				{
					maxCount = count;
					fragmentId = label.id();
				}
			}
			
			colorStream.setActive( fragmentId );
			viewer.requestRepaint();
		}
		
		return fragmentId;
	}

	////////////////
	// behavioUrs //
	////////////////

	private abstract class SelfRegisteringBehaviour implements Behaviour
	{
		private final String name;

		private final String[] defaultTriggers;
		
		public SelfRegisteringBehaviour( final String name, final String ... defaultTriggers )
		{
			this.name = name;
			this.defaultTriggers = defaultTriggers;
		}

		public void register()
		{
			behaviourMap.put( name, this );
			inputAdder.put( name, defaultTriggers );
		}
	}

	private abstract class SelfRegisteringAction extends AbstractNamedAction
	{
		private final String[] defaultTriggers;
		
		public SelfRegisteringAction( final String name, final String ... defaultTriggers )
		{
			super( name );
			this.defaultTriggers = defaultTriggers;
		}

		public void register()
		{
			ksActionAdder.put( this );
			ksKeyStrokeAdder.put( name(), defaultTriggers );
		}
	}
	
	private class SelectFragment extends SelfRegisteringBehaviour implements ClickBehaviour
	{	
		public SelectFragment( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}
		
		@Override
		public void click( int x, int y )
		{
			activeFragmentId = getFragmentIdByDisplayCoordinate( x, y );
			colorStream.setActive( activeFragmentId );
			viewer.requestRepaint();
		}
	}
		
	private class MergeFragment extends SelfRegisteringBehaviour implements ClickBehaviour
	{	
		public MergeFragment( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}
		
		@Override
		public void click( int x, int y )
		{
			final long oldActiveFragmentId = activeFragmentId;
			activeFragmentId = getFragmentIdByDisplayCoordinate( x, y );
			assignment.mergeFragmentSegments( oldActiveFragmentId, activeFragmentId );
			colorStream.setActive( activeFragmentId );
			viewer.requestRepaint();
			
			System.out.println("merged " + oldActiveFragmentId + " with " + activeFragmentId);
		}
	}
		
	private class DetachFragment extends SelfRegisteringBehaviour implements ClickBehaviour
	{	
		public DetachFragment( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}
		
		@Override
		public void click( int x, int y )
		{
			activeFragmentId = getFragmentIdByDisplayCoordinate( x, y );	
			assignment.detachFragment( activeFragmentId );
			colorStream.setActive( activeFragmentId );
			viewer.requestRepaint();

			System.out.println("detached " + activeFragmentId);
		}
	}

	private class ExportAssignments extends SelfRegisteringAction
	{
		private static final long serialVersionUID = 1L;

		public ExportAssignments( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			System.out.println( gson.toJson( assignment ) );
		}
	}
}
