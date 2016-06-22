package cz.cas.mbu.cytimeseries.internal.dataimport;

import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

import cz.cas.mbu.cytimeseries.DataSeriesManager;
import cz.cas.mbu.cytimeseries.DataSeriesStorageProvider;
import cz.cas.mbu.cytimeseries.dataimport.DataSeriesImportManager;

public class ImportDataSeriesTaskFactory extends AbstractTaskFactory {

	private final DataSeriesManager dataSeriesManager;
	private final DataSeriesImportManager importManager;

	
	
	public ImportDataSeriesTaskFactory(DataSeriesManager dataSeriesManager, DataSeriesImportManager importManager) {
		super();
		this.dataSeriesManager = dataSeriesManager;
		this.importManager = importManager;
	}



	@Override
	public TaskIterator createTaskIterator() {
		ImportDataSeriesTask importTask = new ImportDataSeriesTask(dataSeriesManager, importManager);
		AskForInputFileTask inputFileTask = new AskForInputFileTask("Choose input file", 
				file -> importTask.importParameters.setFile(file)
				);
		return new TaskIterator(inputFileTask, importTask);
	}

}