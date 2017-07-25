package bdv.bigcat.viewer.state;

public interface StateListener< T extends AbstractState< T > >
{

	public void stateChanged();

}
