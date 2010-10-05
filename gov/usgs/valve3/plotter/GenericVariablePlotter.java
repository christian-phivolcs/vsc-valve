package gov.usgs.valve3.plotter;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.PointRenderer;
import gov.usgs.plot.ShapeRenderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.util.Pool;
import gov.usgs.util.UtilException;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Column;
import gov.usgs.vdx.data.GenericDataMatrix;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generate images for generic data plot to files
 * 
 * @author Tom Parker
 */
public class GenericVariablePlotter extends RawDataPlotter
{

	private GenericDataMatrix data;
	
	private List<Column> leftColumns;
	private List<Column> rightColumns;

	/**
	 * Default constructor
	 */
	public GenericVariablePlotter(){
		super();
	}

	/**
	 * Gets binary data from VDX server.
	 * @throws Valve3Exception
	 */
	protected void getData(PlotComponent component) throws Valve3Exception
	{
		Map<String, String> params = new LinkedHashMap<String, String>();
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("cid", ch);
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("selectedTypes", component.getString("selectedTypes"));
		addDownsamplingInfo(params);
		try{
			data = (GenericDataMatrix)client.getBinaryData(params);
		}
		catch(UtilException e){
			throw new Valve3Exception(e.getMessage()); 
		}
		pool.checkin(client);
		
		if (data == null || data.rows() == 0)
			throw new Valve3Exception("No data.");
		data.adjustTime(component.getOffset(startTime));
	}

	/**
	 * Initialize internal data from PlotComponent component
	 * @throws Valve3Exception
	 */
	protected void getInputs(PlotComponent component) throws Valve3Exception
	{
		parseCommonParameters(component);
		//ToDo: check for one channel in ch only
		String[] types = component.getString("dataTypes").split("\\$");
		String[] selectedTypes = component.getString("selectedTypes").split(":");
		int c = 0;
		for (int i = 0; i<types.length; i++)
		{
			int type = Integer.parseInt(types[i].split(":")[0]);
			String description = types[i].split(":")[1];
			boolean sel = false;
			
			for (int j = 0; j<selectedTypes.length; j++)
				if (Integer.parseInt(selectedTypes[j]) == type)
					sel = true;
			
			if (!sel)
				continue;		
			
			String columnSpec = c++ + ":" + type + ":" + description + ":" + description + ":T";
			
			Column col = new Column(columnSpec);
			
			if (leftUnit != null && leftUnit.equals(col.unit))
				leftColumns.add(col);
			else if (rightUnit != null && rightUnit.equals(col.unit))
				rightColumns.add(col);
			else if (leftUnit == null)
			{
				leftUnit = col.unit;
				leftColumns = new ArrayList<Column>();
				leftColumns.add(col);
			}
			else if (rightUnit == null)
			{
				rightUnit = col.unit;
				rightColumns = new ArrayList<Column>();
				rightColumns.add(col);
			}
			else
				throw new Valve3Exception("Too many different units.");
		}
		
		if (leftUnit == null && rightUnit == null)
			throw new Valve3Exception("Nothing to plot.");
		
		if (rightUnit != null)
		{
			int minRight = Integer.MAX_VALUE;
			for (Column col : rightColumns)
				minRight = Math.min(minRight, col.idx);
			
			int minLeft = Integer.MAX_VALUE;
			for (Column col : leftColumns)
				minLeft = Math.min(minLeft, col.idx);
			
			if (minLeft > minRight)
			{
				String tempUnit = leftUnit;
				List<Column> tempColumns = leftColumns;
				leftUnit = rightUnit;
				leftColumns = rightColumns;
				rightUnit = tempUnit;
				rightColumns = tempColumns;
			}
		}
		// set up the legend 
		channelLegendsCols	= new String  [leftColumns.size() + rightColumns.size()];
		for (int i = 0; i < leftColumns.size(); i++) {
			channelLegendsCols[i] = String.format("%s", leftColumns.get(i).description);
		}
		for (int i = leftColumns.size(); i < channelLegendsCols.length; i++) {
			channelLegendsCols[i] = String.format("%s", rightColumns.get(i-leftColumns.size()).description);
		}
	}

	/**
	 * Initialize MatrixRenderer for left plot axis
	 * @throws Valve3Exception
	 */
	private MatrixRenderer getLeftMatrixRenderer(PlotComponent component) throws Valve3Exception
	{
		double timeOffset = component.getOffset(startTime);
		MatrixRenderer mr = new MatrixRenderer(data.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		
		double max = -1E300;
		double min = 1E300;
		
		mr.setAllVisible(false);
		for (Column col : leftColumns)
		{
			mr.setVisible(col.idx, true);
			if (col.name.equals("45"))
				data.sum(col.idx+1);

			max = Math.max(max, data.max(col.idx + 1));
			min = Math.min(min, data.min(col.idx + 1));
			max += Math.abs(max - min) * .1;
			min -= Math.abs(max - min) * .1;
		}
		mr.setExtents(startTime+timeOffset, endTime+timeOffset, min, max);	
		mr.createDefaultAxis(xTickMarks?8:0, yTickMarks?8:0, false, true, yTickValues);
		if(shape==null){
			mr.createDefaultPointRenderers(component.getColor());
		} else {
			if (shape.equals("l")) {
				mr.createDefaultLineRenderers(component.getColor());
			} else {
				mr.createDefaultPointRenderers(shape.charAt(0), component.getColor());
			}
		}
		mr.setXAxisToTime(xTickMarks?8:0, xTickValues);
		if(yLabel){
			mr.getAxis().setLeftLabelAsText(leftColumns.get(0).description, Color.blue);
		}
		if(xUnits){
			mr.getAxis().setBottomLabelAsText("Time (" + component.getTimeZone().getID()+ ")");
		}
		if(isDrawLegend) mr.createDefaultLegendRenderer(channelLegendsCols);
		return mr;
	}

	/**
	 * Initialize MatrixRenderer for right plot axis
	 * @throws Valve3Exception
	 */
	private MatrixRenderer getRightMatrixRenderer(PlotComponent component) throws Valve3Exception
	{
		if (rightUnit == null)
			return null;
		double timeOffset = component.getOffset(startTime);
		MatrixRenderer mr = new MatrixRenderer(data.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		
		double max = -1E300;
		double min = 1E300;
		
		mr.setAllVisible(false);
		for (Column col : rightColumns)
		{
			mr.setVisible(col.idx, true);
			if (col.name.equals("45"))
				data.sum(col.idx+1);

			max = Math.max(max, data.max(col.idx + 1));
			min = Math.min(min, data.min(col.idx + 1));
			max += Math.abs(max - min) * .1;
			min -= Math.abs(max - min) * .1;
		}
		mr.setExtents(startTime+timeOffset, endTime+timeOffset, min, max);
		AxisRenderer ar = new AxisRenderer(mr);
		if(yTickValues){
			ar.createRightTickLabels(SmartTick.autoTick(min, max, 8, false), null);
		}
		mr.setAxis(ar);
		if(shape==null){
			mr.createDefaultPointRenderers(component.getColor());
			PointRenderer[] r = (PointRenderer[])mr.getPointRenderers();
			r[1].color = Color.red;
		} else {
			if (shape.equals("l")) {
				mr.createDefaultLineRenderers(component.getColor());
				ShapeRenderer[] r = (ShapeRenderer[])mr.getLineRenderers();
				r[1].color = Color.red;
			} else {
				mr.createDefaultPointRenderers(shape.charAt(0), component.getColor());
				PointRenderer[] r = (PointRenderer[])mr.getPointRenderers();
				r[1].color = Color.red;
			}
		}
		if(yLabel){
			mr.getAxis().setRightLabelAsText(rightColumns.get(0).description, Color.red);
		}
		if(isDrawLegend) mr.createDefaultLegendRenderer(channelLegendsCols);
		return mr;
	}

	/**
	 * Initialize MatrixRenderers for left and right axis,
	 * adds them to plot
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception
	{
		MatrixRenderer leftMR = getLeftMatrixRenderer(component);
		MatrixRenderer rightMR = getRightMatrixRenderer(component);
		v3Plot.getPlot().addRenderer(leftMR);
		if (rightMR != null)
			v3Plot.getPlot().addRenderer(rightMR);
		
		component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
	}

	/**
	 * Concrete realization of abstract method. 
	 * Initialize MatrixRenderers for left and right axis
	 * (plot may have 2 different value axis)
	 * Generate PNG image to file with random file name.
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception
	{
		getInputs(comp);
		getData(comp);

		Plot plot = v3p.getPlot();
		plot.setBackgroundColor(Color.white);
		
		plotData(v3p, comp);

		v3p.addComponent(comp);
		//v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + ":" + comp.get("ch"));
		addSuppData( vdxSource, vdxClient, v3p, comp );
		v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + ": " + comp.get("selectedStation"));
		v3p.setFilename(PlotHandler.getRandomFilename());
		plot.writePNG(Valve3.getInstance().getApplicationPath() + File.separatorChar + v3p.getFilename());
	}

}
