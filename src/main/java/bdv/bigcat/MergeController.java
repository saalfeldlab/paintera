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
import java.util.LinkedList;
import java.util.List;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.ScrollBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bdv.bigcat.ui.AbstractSaturatedARGBStream;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.Multiset.Entry;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.AbstractNamedAction;
import bdv.util.AbstractNamedAction.NamedActionAdder;
import bdv.util.Affine3DHelpers;
import bdv.viewer.InputActionBindings;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 * @author Jan Funke &lt;jfunke@iri.upc.edu&gt;
 */
public class MergeController
{
	final protected ViewerPanel viewer;
	final protected RealRandomAccessible< VolatileLabelMultisetType > labels;
	final protected RealRandomAccess< VolatileLabelMultisetType > labelAccess;
	final protected AbstractSaturatedARGBStream colorStream;
	final protected FragmentSegmentAssignment assignment;
	protected RealPoint lastClick = new RealPoint(3);
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

	private class Action {

		public double x, y, z;

		Action(final double x, final double y, final double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private class Merge extends Action {

		public double prevX, prevY, prevZ;
		public long id1, id2;

		Merge(final double x1, final double y1, final double z1, final double x2, final double y2, final double z2, final long id1, final long id2) {
			super(x2, y2, z2);
			this.prevX = x1;
			this.prevY = y1;
			this.prevZ = z1;
			this.id1 = id1;
			this.id2 = id2;
		}
	}
	private class Split extends Action {

		public long id;

		Split(final double x, final double y, final double z, final long id) {
			super(x, y, z);
			this.id = id;
		}
	}
	private class Paint extends Action {

		Paint(final double x, final double y, final double z) {
			super(x, y, z);
		}
	}

	private List< Merge > merges = new LinkedList< Merge >();
	private List< Split > splits = new LinkedList< Split >();
	private List< Paint > paints = new LinkedList< Paint >();
	// general actions
	private List< Action > actions = new LinkedList< Action >();

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
		inputAdder = config.inputTriggerAdder( inputTriggerMap, "merge" );

		ksKeyStrokeAdder = keyProperties.keyStrokeAdder( ksInputMap, "merge" );

		new SelectFragment("select fragment", "button1").register();
		new NeedMerge("need merge", "shift button1").register();
		new NeedSplit("need split", "control button1").register();
		new NeedPaint("need paint", "control shift button1").register();
		new NeedGeneralAction("need general action", "SPACE button1").register();
		new ExportActions("export assignments", "E").register();
		new IncColorSeed("increase color seed", "C").register();
		new DecColorSeed("decrease color seed", "shift C").register();
		new FixDistanceTranslateZ( 1.0, "scroll browse z", "scroll" ).register();
		new FixDistanceTranslateZ( 10.0, "scroll browse z fast", "shift scroll" ).register();
		new FixDistanceTranslateZ( 0.1, "scroll browse z slow", "ctrl scroll" ).register();


		inputActionBindings.addActionMap( "merge", ksActionMap );
		inputActionBindings.addInputMap( "merge", ksInputMap );
	}

	public long getActiveFragmentId()
	{
		return activeFragmentId;
	}

	/**
	 * Find the id of the fragment that overlaps the most with the given pixel.
	 *
	 * @param x
	 * @param y
	 * @return
	 */
	private long getFragmentIdByDisplayCoordinate( final int x, final int y )
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
			for ( final Entry< Label > entry : labelValues.get().entrySet() )
			{
				final Label label = entry.getElement();
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
		public void click( final int x, final int y )
		{
			activeFragmentId = getFragmentIdByDisplayCoordinate( x, y );
			viewer.displayToGlobalCoordinates(x, y, lastClick);
			colorStream.setActive( activeFragmentId );
			viewer.requestRepaint();
		}
	}

	private class NeedMerge extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public NeedMerge( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			final long oldActiveFragmentId = activeFragmentId;
			activeFragmentId = getFragmentIdByDisplayCoordinate( x, y );
			assignment.mergeFragmentSegments( oldActiveFragmentId, activeFragmentId );
			colorStream.setActive( activeFragmentId );
			viewer.requestRepaint();

			final RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);
			final Merge merge = new Merge(
					lastClick.getDoublePosition(0),
					lastClick.getDoublePosition(1),
					lastClick.getDoublePosition(2),
					pos.getDoublePosition(0),
					pos.getDoublePosition(1),
					pos.getDoublePosition(2),
					oldActiveFragmentId,
					activeFragmentId);
			merges.add(merge);

			viewer.displayToGlobalCoordinates(x, y, lastClick);
			System.out.println("recoreded 'need merge' of " + oldActiveFragmentId + " with " + activeFragmentId);
		}
	}

	private class NeedSplit extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public NeedSplit( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			activeFragmentId = getFragmentIdByDisplayCoordinate( x, y );
			viewer.displayToGlobalCoordinates(x, y, lastClick);
			assignment.detachFragment( activeFragmentId );
			colorStream.setActive( activeFragmentId );
			viewer.requestRepaint();

			final Split split = new Split(
					lastClick.getDoublePosition(0),
					lastClick.getDoublePosition(1),
					lastClick.getDoublePosition(2),
					activeFragmentId);
			splits.add(split);

			System.out.println("recorded 'need-split' of " + activeFragmentId);
		}
	}

	private class NeedPaint extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public NeedPaint( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			activeFragmentId = getFragmentIdByDisplayCoordinate( x, y );
			viewer.displayToGlobalCoordinates(x, y, lastClick);
			colorStream.setActive( activeFragmentId );
			viewer.requestRepaint();

			final Paint paint = new Paint(
					lastClick.getDoublePosition(0),
					lastClick.getDoublePosition(1),
					lastClick.getDoublePosition(2));
			paints.add(paint);

			System.out.println("recorded 'need-paint' at " + lastClick);
		}
	}

	private class NeedGeneralAction extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public NeedGeneralAction( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			activeFragmentId = getFragmentIdByDisplayCoordinate( x, y );
			viewer.displayToGlobalCoordinates(x, y, lastClick);
			colorStream.setActive( activeFragmentId );
			viewer.requestRepaint();

			final Action action = new Action(
					lastClick.getDoublePosition(0),
					lastClick.getDoublePosition(1),
					lastClick.getDoublePosition(2));
			actions.add(action);

			System.out.println("recorded 'need-general-action' at " + lastClick);
		}
	}

	private class ExportActions extends SelfRegisteringAction
	{
		private static final long serialVersionUID = 1L;

		public ExportActions( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			for (final Merge merge : merges)
				System.out.println(
						"merge, " + merge.prevY +
						", " + merge.prevZ +
						", " + Math.round(merge.prevX/10.0) +
						", " + merge.y +
						", " + merge.z +
						", " + Math.round(merge.x/10.0) +
						", " + merge.id1 +
						", " + merge.id2);
			for (final Split split : splits)
				System.out.println(
						"split, " + split.y +
						", " + split.z +
						", " + Math.round(split.x/10.0) +
						", " + split.id);
			for (final Paint paint : paints)
				System.out.println(
						"paint, " + paint.y +
						", " + paint.z +
						", " + Math.round(paint.x/10.0));
			for (final Action action : actions)
				System.out.println(
						"check, " + action.y +
						", " + action.z +
						", " + Math.round(action.x/10.0));
		}
	}

	private class IncColorSeed extends SelfRegisteringAction
	{
		public IncColorSeed( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			colorStream.incSeed();
			colorStream.clearCache();
			viewer.requestRepaint();
		}
	}

	private class DecColorSeed extends SelfRegisteringAction
	{
		public DecColorSeed( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			colorStream.decSeed();
			colorStream.clearCache();
			viewer.requestRepaint();
		}
	}

	private class FixDistanceTranslateZ extends SelfRegisteringBehaviour implements ScrollBehaviour
	{
		final private double speed;

		final private AffineTransform3D affine = new AffineTransform3D();

		public FixDistanceTranslateZ( final double speed, final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
			this.speed = speed;
		}

		@Override
		public void scroll( final double wheelRotation, final boolean isHorizontal, final int x, final int y )
		{
			synchronized ( viewer )
			{
				viewer.getState().getViewerTransform( affine );
				final double dZ = speed * -wheelRotation * Affine3DHelpers.extractScale( affine, 0 );
				affine.set( affine.get( 2, 3 ) - dZ, 2, 3 );
				viewer.setCurrentViewerTransform( affine );
			}
		}
	}
}
