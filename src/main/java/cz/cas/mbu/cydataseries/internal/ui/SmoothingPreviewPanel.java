package cz.cas.mbu.cydataseries.internal.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.jfree.chart.ChartPanel;
import org.omg.CORBA.Current;

import com.google.common.primitives.Doubles;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

import cz.cas.mbu.cydataseries.SmoothingService;
import cz.cas.mbu.cydataseries.TimeSeries;
import cz.cas.mbu.cydataseries.internal.dataimport.MatlabSyntaxNumberList;
import cz.cas.mbu.cydataseries.internal.smoothing.KernelSmoothing;
import cz.cas.mbu.cydataseries.internal.smoothing.LinearKernelSmoothingProvider;
import cz.cas.mbu.cydataseries.internal.smoothing.ParameterDisplayAid;
import cz.cas.mbu.cydataseries.internal.smoothing.SingleParameterSmoothingProvider;
import cz.cas.mbu.cydataseries.internal.tasks.SmoothInteractivePerformTask;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.JSlider;
import javax.swing.SwingConstants;

public class SmoothingPreviewPanel extends JPanel implements CytoPanelComponent {

	
	JComboBox<DisplayFormat> displayGridComboBox;
	private JPanel mainDisplayPanel;
	
	private final CyServiceRegistrar registrar;
	
	private final TimeSeries sourceTimeSeries;
	
	private double[] estimateX = null;
	
	private Map<String,List<Integer>> currentlyShownGroupings;
	private double currentParameter;
	
	private SingleParameterSmoothingProvider currentProvider;
	private ParameterDisplayAid displayAid;
		
	private boolean updatingParameter = false;
	
	private final Color errorTextFieldBackground = new Color(255, 125, 128);
	private final Color defaultTextFieldBackground;
	
	private final Random random;
	
	private String caption;
	private JTextField parameterTextField;
	private JSlider parameterSlider; 
	private JTextField timePointsTextField;
	private final ButtonGroup timePointsButtonGroup = new ButtonGroup();
	private JTextField numEquidistantTextField;
	private JRadioButton rdbtnKeepSourceTimePoints;
	private JRadioButton rdbtnEquidistantTimePoints;
	private JRadioButton rdbtnGivenTimePoints;
	private JLabel lblParameterValue;
	private JLabel lblSmoothingType;
	private JComboBox<ProviderDisplay> providerComboBox;
	
	/**
	 * Create the panel.
	 */
	public SmoothingPreviewPanel(CyServiceRegistrar registrar, TimeSeries timeSeries) {
		setLayout(new BorderLayout(0, 0));
		
		mainDisplayPanel = new JPanel();
		add(mainDisplayPanel, BorderLayout.CENTER);
		
		JPanel controlPanel = new JPanel();
		add(controlPanel, BorderLayout.SOUTH);
		controlPanel.setLayout(new FormLayout(new ColumnSpec[] {
				FormSpecs.RELATED_GAP_COLSPEC,
				FormSpecs.DEFAULT_COLSPEC,
				FormSpecs.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormSpecs.RELATED_GAP_COLSPEC,
				FormSpecs.DEFAULT_COLSPEC,
				FormSpecs.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormSpecs.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormSpecs.RELATED_GAP_COLSPEC,
				FormSpecs.DEFAULT_COLSPEC,},
			new RowSpec[] {
				FormSpecs.UNRELATED_GAP_ROWSPEC,
				FormSpecs.DEFAULT_ROWSPEC,
				FormSpecs.RELATED_GAP_ROWSPEC,
				FormSpecs.DEFAULT_ROWSPEC,
				FormSpecs.RELATED_GAP_ROWSPEC,
				FormSpecs.DEFAULT_ROWSPEC,
				FormSpecs.RELATED_GAP_ROWSPEC,
				FormSpecs.DEFAULT_ROWSPEC,}));
		
		JLabel lblDisplay = new JLabel("Display:");
		controlPanel.add(lblDisplay, "2, 2, right, default");
		
		displayGridComboBox = new JComboBox<>(new DefaultComboBoxModel<DisplayFormat>(new DisplayFormat[] {
				new DisplayFormat(1, 1),
				new DisplayFormat(1, 2),
				new DisplayFormat(1, 3),
				new DisplayFormat(2, 2),				
				new DisplayFormat(3, 2),				
				new DisplayFormat(5, 1),				
				}));
		displayGridComboBox.setSelectedIndex(1);//TODO - use RememberValueService
		displayGridComboBox.addItemListener(evt -> {
			if(evt.getStateChange() == ItemEvent.SELECTED)
			{
				updateDisplayGrid();
			}
		});
		controlPanel.add(displayGridComboBox, "4, 2, fill, default");
		
		JSeparator separator = new JSeparator();
		controlPanel.add(separator, "1, 1, 12, 1");
		
		rdbtnKeepSourceTimePoints = new JRadioButton("Estimate in the same time points as the original series");
		timePointsButtonGroup.add(rdbtnKeepSourceTimePoints);
		controlPanel.add(rdbtnKeepSourceTimePoints, "6, 2, 5, 1");
		rdbtnKeepSourceTimePoints.addItemListener(evt -> timePointsInputChanged());
		
		JButton btnResampleRows = new JButton("See different examples");
		controlPanel.add(btnResampleRows, "12, 2");
		btnResampleRows.addActionListener(evt -> showDifferentExamples());
		
		JButton btnPerformSmoothing = new JButton("Perform smoothing");
		btnPerformSmoothing.setFont(new Font("Tahoma", Font.BOLD, 13));
		btnPerformSmoothing.addActionListener(evt -> performSmoothing());
		
		lblSmoothingType = new JLabel("Smoothing algorithm:");
		controlPanel.add(lblSmoothingType, "2, 4, right, default");
		
		providerComboBox = new JComboBox<>();
		controlPanel.add(providerComboBox, "4, 4, fill, default");
								
		rdbtnEquidistantTimePoints = new JRadioButton("Estimate at N equidistant points:");
		rdbtnEquidistantTimePoints.setSelected(true);
		timePointsButtonGroup.add(rdbtnEquidistantTimePoints);
		controlPanel.add(rdbtnEquidistantTimePoints, "6, 4");		
		rdbtnEquidistantTimePoints.addItemListener(evt -> timePointsInputChanged());
		
		numEquidistantTextField = new JTextField();
		numEquidistantTextField.setText("100");
		controlPanel.add(numEquidistantTextField, "8, 4, 3, 1, fill, default");
		numEquidistantTextField.setColumns(10);
		controlPanel.add(btnPerformSmoothing, "12, 4");
		numEquidistantTextField.getDocument().addDocumentListener(UIUtils.listenForAllDocumentChanges(this::timePointsInputChanged));
		
		JLabel lblSmoothingBandwidth = new JLabel("Smoothing amount:");
		controlPanel.add(lblSmoothingBandwidth, "2, 6, right, default");
		
		parameterSlider = new JSlider();
		controlPanel.add(parameterSlider, "4, 6");
		parameterSlider.addChangeListener(evt -> bandwidthSliderChanged());
		
		rdbtnGivenTimePoints = new JRadioButton("Estimate at specific time points:");
		timePointsButtonGroup.add(rdbtnGivenTimePoints);
		controlPanel.add(rdbtnGivenTimePoints, "6, 6");
		rdbtnGivenTimePoints.addItemListener(evt -> timePointsInputChanged());
		
		timePointsTextField = new JTextField();
		timePointsTextField.setText("1:100");
		timePointsTextField.setEnabled(false);
		controlPanel.add(timePointsTextField, "8, 6, 3, 1, fill, default");
		timePointsTextField.setColumns(10);
		timePointsTextField.getDocument().addDocumentListener(UIUtils.listenForAllDocumentChanges(this::timePointsInputChanged));
		
		JButton btnClose = new JButton("Close");
		controlPanel.add(btnClose, "12, 6");
		
		lblParameterValue = new JLabel("Parameter value:");
		lblParameterValue.setHorizontalAlignment(SwingConstants.TRAILING);
		controlPanel.add(lblParameterValue, "2, 8, right, default");
		
		parameterTextField = new JTextField();
		controlPanel.add(parameterTextField, "4, 8, fill, default");
		parameterTextField.setColumns(10);
		
		this.defaultTextFieldBackground = parameterTextField.getBackground();
		
		JLabel lblCommaSeparatedSupports = new JLabel("<html>Comma separated, supports Matlab notation (e.g. 1,2,3:5,10:2:20). Points outside the original interval are ignored.</html>");
		lblCommaSeparatedSupports.setFont(new Font("Tahoma", Font.ITALIC, 11));
		controlPanel.add(lblCommaSeparatedSupports, "6, 8, 5, 1");
		btnClose.addActionListener(evt -> closePanel());
		
		this.registrar = registrar;
		this.sourceTimeSeries = timeSeries;
		
		currentlyShownGroupings = new HashMap<>();
		random = new Random();
		
		if(!java.beans.Beans.isDesignTime())
		{
			updateEstimateX();
			
			ProviderDisplay[] providerDisplays = registrar.getService(SmoothingService.class).getSmoothingProviders().stream()
					.map(ProviderDisplay::new)
					.toArray(ProviderDisplay[]::new);			
			providerComboBox.setModel(new DefaultComboBoxModel<>(providerDisplays));
			providerComboBox.setSelectedIndex(0);
			
			providerComboBox.addItemListener(evt -> {
				if(evt.getStateChange() == ItemEvent.SELECTED)
				{
					updateProvider();
				}
			});
			
			currentProvider = providerDisplays[0].getProvider();
			displayAid = currentProvider.getDisplayAid(sourceTimeSeries.getIndexArray());
			guessBandwidth();
			updateDisplayedParameter();
			
			//Add listener only after initial assignment to the bandwidth text field
			parameterTextField.getDocument().addDocumentListener(UIUtils.listenForAllDocumentChanges(this::bandwidthTextChanged));
			
			
			sampleShownRows();
			updateDisplayGrid();
			
			this.caption = "Smoothing: " + timeSeries.getName();
		}
		
	}

	private void updateDisplayedParameter() {
		updateDisplayedParameterText();
		updateDisplayedParameterSlider();
	}

	private void updateProvider()
	{
		if(providerComboBox.getSelectedIndex() >= 0)
		{
			currentProvider = providerComboBox.getItemAt(providerComboBox.getSelectedIndex()).getProvider();
			displayAid = currentProvider.getDisplayAid(sourceTimeSeries.getIndexArray());
			
			updatingParameter = true;
			
			if(displayAid == null)
			{
				parameterSlider.setEnabled(false);
				parameterTextField.setEnabled(false);
			}
			else
			{
				parameterSlider.setEnabled(true);
				parameterTextField.setEnabled(true);
				
				currentParameter = displayAid.bestParameterGuess(); 
				updateDisplayedParameter();
			}
			
			updatingParameter = false;
			
			updateDisplayGrid();
		}
	}
	
	private void updateDisplayedParameterText()
	{
		parameterTextField.setText(Double.toString(currentParameter));		
	}
	
	private void updateDisplayedParameterSlider()
	{
		if(displayAid != null)
		{
			parameterSlider.setValue((int)(displayAid.parameterValueToSmoothingAmount(currentParameter) * parameterSlider.getMaximum()));
		}
	}
	
	private void guessBandwidth()
	{
		if(displayAid != null)
		{
			currentParameter = displayAid.bestParameterGuess();
		}
	}
	
	private void sampleShownRows()
	{
		//sample row names
		currentlyShownGroupings.clear();
		
		SmoothingService smoothingService = registrar.getService(SmoothingService.class);
		
		Map<String, List<Integer>> rowGroupings = smoothingService.getDefaultRowGrouping(sourceTimeSeries);

		int numGroupingsToSelect = getMaxDisplayed();
		if(rowGroupings.size() <= numGroupingsToSelect)
		{
			currentlyShownGroupings.putAll(rowGroupings);
		}
		else
		{
			//randomly select maxDisplayed groupings
			List<String> groupingNames = new ArrayList<>(rowGroupings.keySet());
			
			for(int i = 0; i < numGroupingsToSelect;i++)
			{
				int selectedIndex = random.nextInt(groupingNames.size() - i);
				String selectedName = groupingNames.get(selectedIndex);
				currentlyShownGroupings.put(selectedName, rowGroupings.get(selectedName));

				//Shrink the set of values available for choosing
				int lastUsedIndex = groupingNames.size() - i - 1;
				groupingNames.set(selectedIndex, groupingNames.get(lastUsedIndex));
			}
			
		}		 
		
	}
	
	private void updateEstimateX()
	{
		boolean showGivenTimePointsError = false;
		boolean showEquidistantError = false;
		if(rdbtnGivenTimePoints.isSelected())
		{
			try {
				List<Double> timePointsList = MatlabSyntaxNumberList.listFromString(timePointsTextField.getText());
								
				//filter points outside the original data interval
				double minTime = sourceTimeSeries.getIndex().stream().reduce(Double.POSITIVE_INFINITY, Math::min);
				double maxTime = sourceTimeSeries.getIndex().stream().reduce(Double.NEGATIVE_INFINITY, Math::max);
				
				List<Double> timePointsListFiltered = timePointsList.stream()
						.filter(x -> x >= minTime - 0.0001 && x <= maxTime + 0.0001 )
						.collect(Collectors.toList());
				
				if(!timePointsListFiltered.isEmpty())
				{
					estimateX = Doubles.toArray(timePointsListFiltered);
				}
				else
				{
					showGivenTimePointsError = true;
				}
			}
			catch (NumberFormatException ex)
			{
				showGivenTimePointsError = true;
			}
		}
		else if (rdbtnEquidistantTimePoints.isSelected())
		{
			try 
			{
				int numEquidistant = Integer.parseInt(numEquidistantTextField.getText());
				if(numEquidistant < 2)
				{
					showEquidistantError = true;					
				}
				else
				{
					double min = DoubleStream.of(sourceTimeSeries.getIndexArray()).min().orElse(0);
					double max = DoubleStream.of(sourceTimeSeries.getIndexArray()).max().orElse(1);
					
					estimateX = new double[numEquidistant];
					double step = (max - min) / (numEquidistant - 1);
					for(int i = 0; i < numEquidistant; i++)
					{
						estimateX[i] = min + (step * i);
					}
				}
			}
			catch (NumberFormatException ex)
			{
				showEquidistantError = true;
			}
		}
		else 
		{
			estimateX = sourceTimeSeries.getIndexArray();
		}
		
		if(showGivenTimePointsError)
		{
			timePointsTextField.setBackground(errorTextFieldBackground);			
		}
		else
		{
			timePointsTextField.setBackground(defaultTextFieldBackground);
		}
		
		if(showEquidistantError)
		{
			numEquidistantTextField.setBackground(errorTextFieldBackground);			
		}
		else
		{
			numEquidistantTextField.setBackground(defaultTextFieldBackground);						
		}
	}
	
	private void timePointsInputChanged()
	{
		double[] oldEstimateX = estimateX;
		updateEstimateX();
		timePointsTextField.setEnabled(rdbtnGivenTimePoints.isSelected());
		numEquidistantTextField.setEnabled(rdbtnEquidistantTimePoints.isSelected());
		if(oldEstimateX != estimateX) //intentional reference equality - just to bypass the most obvious unnecessary redrawings
		{
			updateDisplayGrid();
		}
	}
	
	@Override
	public Component getComponent() {		
		return this;
	}



	@Override
	public CytoPanelName getCytoPanelName() {
		return CytoPanelName.EAST;
	}



	@Override
	public String getTitle() {
		return caption;
	}



	@Override
	public Icon getIcon() {
		return null;
	}


	private void showDifferentExamples()
	{
		sampleShownRows();
		updateDisplayGrid();
	}

	private void updateDisplayGrid()
	{
		DisplayFormat fmt = getSelectedDisplayFormat();
		mainDisplayPanel.removeAll();

		int maxDisplayed = getMaxDisplayed();		

		//Resample if maxDisplayed increased, unless we are already showing the whole series
		if(maxDisplayed > currentlyShownGroupings.size() && currentlyShownGroupings.size() < sourceTimeSeries.getRowCount())
		{
			sampleShownRows();
		}
		
		ColumnSpec[] layoutColumns = new ColumnSpec[fmt.width * 2 - 1];
		RowSpec[] layoutRows = new RowSpec[(fmt.height) * 2 - 1]; 		
		
		for(int i = 0; i < fmt.width; i++)
		{
			if(i != 0)
			{
				layoutColumns[i * 2 - 1] = FormSpecs.RELATED_GAP_COLSPEC;
			}
			layoutColumns[i * 2] = FormSpecs.DEFAULT_COLSPEC;
		}
		
		for(int i = 0; i < fmt.height; i++) 
		{
			if(i != 0)
			{
				layoutRows[i * 2 - 1] = FormSpecs.RELATED_GAP_ROWSPEC;
			}
			layoutRows[i * 2] = FormSpecs.DEFAULT_ROWSPEC;
			
		}
		
		mainDisplayPanel.setLayout(new FormLayout(layoutColumns, layoutRows));
		
		List<JPanel> panelsToShow = currentlyShownGroupings.entrySet().stream()
				.map(entry -> {
					List<Integer> rows = entry.getValue();
					double[] allRowsConcat = new double[rows.size() * sourceTimeSeries.getIndexCount()];
					double[] repeatedIndex = new double[rows.size() * sourceTimeSeries.getIndexCount()];
					int rowLength = sourceTimeSeries.getIndexCount(); 
					for(int i = 0; i < rows.size(); i++ )
					{
						int row = rows.get(i);
						System.arraycopy(sourceTimeSeries.getRowDataArray(row), 0, allRowsConcat, i * rowLength, rowLength);
						System.arraycopy(sourceTimeSeries.getIndexArray(), 0, repeatedIndex, i * rowLength, rowLength);
					}
				
					//Do the smoothing
					double[] smoothedY = currentProvider.smooth(repeatedIndex, allRowsConcat, estimateX, currentParameter);
					
					SmoothingChartContainer chartContainer = new SmoothingChartContainer();
					chartContainer.setSmoothingData(repeatedIndex, allRowsConcat, estimateX, smoothedY, entry.getKey());
					return new ChartPanel(chartContainer.getChart());
				})
				.collect(Collectors.toList());
		
		CellConstraints cc = new CellConstraints();
		for(int x = 0; x < fmt.getWidth(); x++)
		{
			for(int y = 0; y < fmt.getHeight(); y++)
			{
				int index = (y * fmt.getWidth()) + x;
				JPanel panelToAdd;
				if(index >= panelsToShow.size())
				{
					panelToAdd = new JPanel();
				}
				else
				{
					panelToAdd = panelsToShow.get(index);
				}
				
				int layoutX = x * 2 + 1;
				int layoutY = y * 2 + 1;				
				mainDisplayPanel.add(panelToAdd, cc.xy(layoutX, layoutY));
			}
		}
		

		mainDisplayPanel.revalidate();
		mainDisplayPanel.repaint();
	}

	private int getMaxDisplayed() {
		DisplayFormat fmt = getSelectedDisplayFormat();
		return fmt.getWidth() * fmt.getHeight();
	}

	private DisplayFormat getSelectedDisplayFormat() {
		return displayGridComboBox.getItemAt(displayGridComboBox.getSelectedIndex());
	}
	
	private void bandwidthSliderChanged()
	{
		if(updatingParameter)
		{
			return;
		}
		try 
		{
			updatingParameter = true;
			double smoothingAmount = ((double)parameterSlider.getValue()) / ((double)parameterSlider.getMaximum()) ;
			currentParameter = displayAid.smoothingAmountToParameterValue(smoothingAmount);			
			updateDisplayedParameterText();
			updateDisplayGrid();
		}
		finally
		{
			updatingParameter = false;
		}
	}
	
	private void bandwidthTextChanged()
	{
		if(updatingParameter)
		{
			return;
		}
		try 
		{
			updatingParameter = true;
			
			boolean showError = false;
			try 
			{
				double bandwidth = Double.parseDouble(parameterTextField.getText());
				if (bandwidth > 0 && Double.isFinite(bandwidth))
				{
					currentParameter = bandwidth;
					updateDisplayedParameterSlider();
					updateDisplayGrid();
				}
				else
				{
					showError = true;
				}
			} catch (NumberFormatException ex)
			{
				showError = true;
			}
			
			if(showError)
			{
				parameterTextField.setBackground(errorTextFieldBackground);
			}
			else
			{
				parameterTextField.setBackground(defaultTextFieldBackground);
			}			
		}
		finally
		{
			updatingParameter = false;
		}
		
	}
	
	private void performSmoothing()
	{
		Map<String, List<Integer>> rowGrouping = registrar.getService(SmoothingService.class).getDefaultRowGrouping(sourceTimeSeries);
		registrar.getService(TaskManager.class).execute(new TaskIterator(new SmoothInteractivePerformTask(registrar, sourceTimeSeries, estimateX, currentProvider, currentParameter, rowGrouping, this)));
	}
	
	public void closePanel()
	{
		registrar.unregisterAllServices(this);
	}
	
	private static class DisplayFormat
	{
		private final int width;
		private final int height;
		
		public DisplayFormat(int width, int height) {
			super();
			this.width = width;
			this.height = height;
		}

		public int getWidth() {
			return width;
		}

		public int getHeight() {
			return height;
		}

		@Override
		public String toString() {
			return width + "x" + height;
		}
		
		
	}
			

	private static class ProviderDisplay {
		private final SingleParameterSmoothingProvider provider;

		public ProviderDisplay(SingleParameterSmoothingProvider provider) {
			super();
			this.provider = provider;
		}
		
		public SingleParameterSmoothingProvider getProvider() {
			return provider;
		}

		@Override
		public String toString() {
			return provider.getName();
		}
		
		
	}
}
