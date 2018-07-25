package org.janelia.saalfeldlab.paintera.state;

import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.type.numeric.RealType;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.DelegatingDataSource;

public class InvertingRawSourceState<D, T extends RealType<T>>
		extends MinimalSourceState<D, T, DataSource<D, T>, ARGBColorConverter<T>>
{

	public InvertingRawSourceState(
			final String name,
			final RawSourceState<D, T> dependsOn)
	{
		super(
				duplicate(dependsOn.dataSource()),
				new ARGBColorConverter.InvertingImp1<>(0, 255),
				dependsOn.compositeProperty().get(),
				name,
				dependsOn
		     );
		this.converter().minProperty().bindBidirectional(dependsOn.converter().maxProperty());
		this.converter().maxProperty().bindBidirectional(dependsOn.converter().minProperty());
		this.converter().colorProperty().bindBidirectional(dependsOn.converter().colorProperty());

		this.isVisibleProperty().addListener((obs, oldv, newv) -> dependsOn.isVisibleProperty().set(!newv.booleanValue
				()));
		dependsOn.isVisibleProperty().addListener((obs, oldv, newv) -> this.isVisibleProperty().set(!newv.booleanValue
				()));
		this.isVisibleProperty().set(!dependsOn.isVisibleProperty().get());

		this.compositeProperty().bindBidirectional(dependsOn.compositeProperty());
		this.interpolationProperty().bindBidirectional(dependsOn.interpolationProperty());
	}

	private static <D, T> DataSource<D, T> duplicate(final DataSource<D, T> source)
	{
		return new DelegatingDataSource<>(source);
	}

}
