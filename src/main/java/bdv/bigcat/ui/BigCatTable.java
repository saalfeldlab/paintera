package bdv.bigcat.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

public class BigCatTable extends JTable implements TableModelListener {

	private static final long serialVersionUID = 1L;

	private static final Color EVEN_ROW_COLOR = new Color(206, 233, 242);
	private static final Color ODD_ROW_COLOR = new Color(215, 242, 206);
	private static final Color SELECTED_ROW_COLOR = new Color(255, 235, 226);

	private static final int MIN_COLUMN_WIDTH = 50;
	
	private boolean autoResizeCols = false;
	
	public BigCatTable(TableModel tm) {
		super(tm);
		init();
	
	}

	private void init() {
		setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		setIntercellSpacing(new Dimension(2, 2));
		getModel().addTableModelListener(this);
	}

	public void setAutoResizeColumns(boolean value) {
		
		this.autoResizeCols = value;
		
		if (autoResizeCols)
			autoResizeColumns();
	}
		
	private void autoResizeColumns() {
				
		final TableColumnModel columnModel = getColumnModel();
		for (int column = 0; column < getColumnCount(); column++) {
			int width = MIN_COLUMN_WIDTH;
			for (int row = 0; row < getRowCount(); row++) {
				TableCellRenderer renderer = getCellRenderer(row, column);
				Component comp = prepareRenderer(renderer, row, column);
				width = Math.max(comp.getPreferredSize().width + 10, width);
			}
			columnModel.getColumn(column).setPreferredWidth(width);
		}
	}

	@Override
	public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
		
		Component component = super.prepareRenderer(renderer, row, column);
		if (isRowSelected(row))
			component.setBackground(SELECTED_ROW_COLOR);
		else
			component.setBackground(row % 2 == 0 ? EVEN_ROW_COLOR : ODD_ROW_COLOR);
		component.setForeground(Color.BLACK);
		return component;
	}
	
	@Override
	public void tableChanged(TableModelEvent e) {
		super.tableChanged(e);
		
		if (autoResizeCols)
			autoResizeColumns();
	}
}