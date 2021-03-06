package cz.cas.mbu.cydataseries.internal.data;

import java.util.List;

/**
 * An abstact class for all DS whose index is stored as list (as contrasted e.g. to the array used in {@link TimeSeriesImpl}.
 * @author MBU
 *
 * @param <INDEX>
 * @param <DATA>
 */
public abstract class AbstractListIndexDataSeries<INDEX,DATA> extends AbstractDataSeries<INDEX, DATA> {

	private List<INDEX> indexData;
	private Class<INDEX> indexClass;

	public AbstractListIndexDataSeries(Long suid, String name, int[] rowIds, List<String> rowNames, List<INDEX> indexData,
			Class<INDEX> indexClass) {
		super(suid, name, rowIds, rowNames);
		this.indexData = indexData;
		this.indexClass = indexClass;
	}

	@Override
	public Class<INDEX> getIndexClass() {
		return indexClass;
	}

	@Override
	public List<INDEX> getIndex() {
		return indexData;
	}

}
