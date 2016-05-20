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
package bdv.bigcat.control;

import net.imglib2.realtransform.AffineTransform3D;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.ScrollBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.util.Affine3DHelpers;
import bdv.viewer.ViewerPanel;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 * @author Jan Funke &lt;jfunke@iri.upc.edu&gt;
 */
public class TranslateZController
{
	final protected ViewerPanel viewer;

	// for behavioUrs
	private final BehaviourMap behaviourMap = new BehaviourMap();
	private final InputTriggerMap inputTriggerMap = new InputTriggerMap();
	private final InputTriggerAdder inputAdder;

	public TranslateZController(
			final ViewerPanel viewer,
			final double[] resolution,
			final InputTriggerConfig config)
	{
		this.viewer = viewer;
		inputAdder = config.inputTriggerAdder( inputTriggerMap, "translate_z" );

		double min = Math.min(Math.min(resolution[0], resolution[1]), resolution[2]);
		double max = Math.max(Math.max(resolution[0], resolution[1]), resolution[2]);
		double f = max/min;

//		// fast scrolling is the default, normal and slow with modifiers
//		new FixDistanceTranslateZ( max, "scroll browse z fast", "scroll" ).register();
//		new FixDistanceTranslateZ( min, "scroll browse z", "shift scroll" ).register();
//		new FixDistanceTranslateZ( min/f, "scroll browse z slow", "ctrl scroll" ).register();

		/*
		 * The above doesn't work, "scroll" still triggers "scroll browse z",
		 * might be related to bdv mapping with same name...
		 *
		 * So we have to do things with the wrong names...
		 */
		new FixDistanceTranslateZ( min, "scroll browse z fast", "shift scroll" ).register();
		new FixDistanceTranslateZ( max, "scroll browse z", "scroll" ).register();
		new FixDistanceTranslateZ( min/f, "scroll browse z slow", "ctrl scroll" ).register();
	}



	public BehaviourMap getBehaviourMap()
	{
		return behaviourMap;
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
				System.out.println("wheel rotation: " + wheelRotation);
				System.out.println("speed: " + speed);
				viewer.getState().getViewerTransform( affine );
				final double dZ = speed * -wheelRotation * Affine3DHelpers.extractScale( affine, 0 );
				affine.set( affine.get( 2, 3 ) - dZ, 2, 3 );
				viewer.setCurrentViewerTransform( affine );
			}
		}
	}
}
