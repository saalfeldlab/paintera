package bdv.bigcat.control;

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
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

/**
 * @autoher Philipp Hanslovsky &lt;hanslovskyp@janelia.hhmi.org&gt;
 */
public class ModeToggleController {

    public final static String BLOCKING_MAP_NAME="BLOCKING_MAP";

    public static abstract class SelfRegisteringAction extends AbstractNamedAction
    {
        private final String[] defaultTriggers;

        public SelfRegisteringAction( final String name, final String ... defaultTriggers )
        {
            super( name );
            this.defaultTriggers = defaultTriggers;
        }

        public void register( final ActionMap actionMap, final KeyStrokeAdder ksKeyStrokeAdder )
        {
            put( actionMap );
            ksKeyStrokeAdder.put( name(), defaultTriggers );
        }
    }


    public static abstract class SelfRegisteringBehaviour implements Behaviour
    {
        private final String name;

        private final String[] defaultTriggers;

        protected String getName()
        {
            return name;
        }

        public SelfRegisteringBehaviour( final String name, final String... defaultTriggers )
        {
            this.name = name;
            this.defaultTriggers = defaultTriggers;
        }

        public void register( BehaviourMap behaviourMap, InputTriggerAdder inputAdder )
        {
            behaviourMap.put( name, this );
            inputAdder.put( name, defaultTriggers );
        }
    }


    public static abstract class AbstractToggle extends SelfRegisteringAction
    {

        // protected or private?

        private final TriggerBehaviourBindings bindings;
        private final InputActionBindings inputActionBindings;

        private final InputMap ksWithinModeInputMap;
        private final ActionMap ksWithinModeActionMap;

        private final BehaviourMap withinModeBehaviourMap;
        private final InputTriggerMap withinModeInputTriggerMap;

        public AbstractToggle(
                final TriggerBehaviourBindings bindings,
                final InputActionBindings inputActionBindings,
                final InputMap ksWithinModeInputMap,
                final ActionMap ksWithinModeActionMap,
                final BehaviourMap withinModeBehaviourMap,
                final InputTriggerMap withinModeInputTriggerMap,
                String name,
                String... defaultTriggers ) {
            super( name, defaultTriggers );
            this.bindings = bindings;
            this.inputActionBindings = inputActionBindings;
            this.ksWithinModeInputMap = ksWithinModeInputMap;
            this.ksWithinModeActionMap = ksWithinModeActionMap;
            this.withinModeBehaviourMap = withinModeBehaviourMap;
            this.withinModeInputTriggerMap = withinModeInputTriggerMap;
        }

        public AbstractToggle(
                final TriggerBehaviourBindings bindings,
                final InputActionBindings inputActionBindings,
                String name,
                String... defaultTriggers
        )
        {
            this(
                    bindings,
                    inputActionBindings,
                    new InputMap(),
                    new ActionMap(),
                    new BehaviourMap(),
                    new InputTriggerMap(),
                    name,
                    defaultTriggers );
        }

        @Override
        public void actionPerformed( ActionEvent actionEvent ) {
            System.out.println( "Everyday I'm toggling" );
            bindings.addInputTriggerMap( BLOCKING_MAP_NAME, withinModeInputTriggerMap, "all" );
            bindings.addBehaviourMap( BLOCKING_MAP_NAME, withinModeBehaviourMap );

            inputActionBindings.addInputMap( BLOCKING_MAP_NAME, ksWithinModeInputMap, "all" );
            inputActionBindings.addActionMap( BLOCKING_MAP_NAME, ksWithinModeActionMap );

            actionImplementation();

        }

        public abstract void actionImplementation();

    }


    public static class NoActionToggle extends AbstractToggle
    {
        public NoActionToggle(
                final TriggerBehaviourBindings bindings,
                final InputActionBindings inputActionBindings,
                final InputMap ksWithinModeInputMap,
                final ActionMap ksWithinModeActionMap,
                String name,
                String... defaultTriggers
        )
        {
            super(
                    bindings,
                    inputActionBindings,
                    ksWithinModeInputMap,
                    ksWithinModeActionMap,
                    new BehaviourMap(),
                    new InputTriggerMap(),
                    name,
                    defaultTriggers );
        }

        @Override
        public void actionImplementation() {

        }
    }

    public static abstract class AbstractUnToggle extends SelfRegisteringAction
    {

        private final TriggerBehaviourBindings bindings;
        private final InputActionBindings inputActionBindings;

        public AbstractUnToggle(
                final TriggerBehaviourBindings bindings,
                final InputActionBindings inputActionBindings,
                String name,
                String... defaultTriggers
        ) {
            super( name, defaultTriggers );
            this.bindings = bindings;
            this.inputActionBindings = inputActionBindings;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            doOnUnToggle();
            this.bindings.removeBehaviourMap( BLOCKING_MAP_NAME );
            this.bindings.removeInputTriggerMap( BLOCKING_MAP_NAME );

            this.inputActionBindings.removeActionMap( BLOCKING_MAP_NAME );
            this.inputActionBindings.removeInputMap( BLOCKING_MAP_NAME );

        }

        protected abstract void doOnUnToggle();

    }


    public static abstract class AbstractUnToggleOnClick extends SelfRegisteringBehaviour implements ClickBehaviour
    {

        private final TriggerBehaviourBindings bindings;
        private final InputActionBindings inputActionBindings;

        public AbstractUnToggleOnClick(
                final TriggerBehaviourBindings bindings,
                final InputActionBindings inputActionBindings,
                String name,
                String... defaultTriggers
        ) {
            super( name, defaultTriggers );
            this.bindings = bindings;
            this.inputActionBindings = inputActionBindings;
        }

        @Override
        public void click( int x, int y )
        {
            doOnUnToggle( x, y );
            this.bindings.removeBehaviourMap( BLOCKING_MAP_NAME );
            this.bindings.removeInputTriggerMap( BLOCKING_MAP_NAME );

            this.inputActionBindings.removeActionMap( BLOCKING_MAP_NAME );
            this.inputActionBindings.removeInputMap( BLOCKING_MAP_NAME );

        }

        protected abstract void doOnUnToggle( int x, int y );

    }


    public static class NoActionUnToggle extends AbstractUnToggle {

        public NoActionUnToggle(
                final TriggerBehaviourBindings bindings,
                final InputActionBindings inputActionBindings,
                String name,
                String... defaultTriggers
        ) {
            super( bindings, inputActionBindings, name, defaultTriggers );
        }

        @Override
        protected void doOnUnToggle() {
            System.out.println( "doUnToggle" );
        }
    }

    public static void registerToggle(
            final InputTriggerConfig config,
            final InputActionBindings inputActionBindings,
            final AbstractToggle toggle,
            final String controllerName
    )
    {

        final InputMap ksGlobalInputMap = new InputMap();
        final ActionMap ksGlobalActionMap = new ActionMap();

        KeyStrokeAdder ksGlobalKeyStrokeAdder = config.keyStrokeAdder( ksGlobalInputMap, controllerName );
        toggle.register( ksGlobalActionMap, ksGlobalKeyStrokeAdder );
        inputActionBindings.addActionMap( controllerName, ksGlobalActionMap );
        inputActionBindings.addInputMap( controllerName, ksGlobalInputMap );

    }

    public static class ExecuteOnUnToggle extends AbstractUnToggle
    {
        private final Runnable action;

        public ExecuteOnUnToggle(
                final Runnable action,
                final TriggerBehaviourBindings bindings,
                final InputActionBindings inputActionBindings,
                final String name,
                final String... defaultTriggers) {
            super(bindings, inputActionBindings, name, defaultTriggers);
            this.action = action;
        }

        @Override
        protected void doOnUnToggle() {
            action.run();
        }
    }


    public static class ExecuteOnUnToggleOnClick extends AbstractUnToggleOnClick
    {
        private final Runnable action;

        public ExecuteOnUnToggleOnClick(
                final Runnable action,
                final TriggerBehaviourBindings bindings,
                final InputActionBindings inputActionBindings,
                final String name,
                final String... defaultTriggers) {
            super(bindings, inputActionBindings, name, defaultTriggers);
            this.action = action;
        }

        @Override
        protected void doOnUnToggle( int x, int y ) {
            action.run();
        }
    }


    public static void noOpToggle(
            final InputTriggerConfig config,
            final InputActionBindings inputActionBindings,
            final TriggerBehaviourBindings bindings,
            final String controllerName
    )
    {
        final InputMap ksWithinModeInputMap = new InputMap();
        final ActionMap ksWithinModeActionMap = new ActionMap();
        final KeyStrokeAdder ksWithinModeInputAdder = config.keyStrokeAdder(ksWithinModeInputMap, "within mode");

        NoActionUnToggle unToggle = new NoActionUnToggle( bindings, inputActionBindings, "untoggle", "T" );
        unToggle.register( ksWithinModeActionMap, ksWithinModeInputAdder );

        NoActionToggle toggle = new NoActionToggle(
                bindings,
                inputActionBindings,
                ksWithinModeInputMap,
                ksWithinModeActionMap,
                "toggle",
                "shift T");

        ModeToggleController.registerToggle( config, inputActionBindings, toggle, controllerName );

    }


}
