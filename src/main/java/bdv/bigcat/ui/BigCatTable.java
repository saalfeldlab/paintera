package bdv.bigcat.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

public class BigCatTable extends JTable implements TableModelListener {

	private static final long serialVersionUID = 1L;

	private static final Color EVEN_ROW_COLOR = new Color(206, 233, 242);
	private static final Color ODD_ROW_COLOR = new Color(215, 242, 206);
	private static final Color SELECTED_ROW_COLOR = new Color(255, 235, 226);

	private static final int MIN_COLUMN_WIDTH = 50;
	private static final double ENLARGE_COLUMN_WIDTH = 1.05;
	
	private boolean inLayout = false;
	private boolean initialized = false;
	
	// don't change column widths that the user set manually
	private final Set<Integer> manuallyChangedColumuns = new HashSet<Integer>();
	
	public BigCatTable(TableModel tm) {
		super(tm);
		setSurrendersFocusOnKeystroke(true);
		setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		setIntercellSpacing(new Dimension(2, 2));
		getModel().addTableModelListener(this);
		initialized = true;
		setPreferredColumnWidths();
	}

    @Override
    public boolean getScrollableTracksViewportWidth() {
    	
        return hasExcessWidth();
    }

    @Override
    public void doLayout() {
    	
        if (hasExcessWidth())
            autoResizeMode = AUTO_RESIZE_SUBSEQUENT_COLUMNS;
        
        inLayout = true;
        super.doLayout();
        inLayout = false;
        
        autoResizeMode = AUTO_RESIZE_OFF;
    }

    @Override
    public void columnMarginChanged(ChangeEvent e) {

        if (isEditing())
            removeEditor();
        
        TableColumn resizingColumn = getTableHeader().getResizingColumn();

        
        if (resizingColumn != null && autoResizeMode == AUTO_RESIZE_OFF && !inLayout) {
        	
            resizingColumn.setPreferredWidth(resizingColumn.getWidth());
            manuallyChangedColumuns.add(resizingColumn.getModelIndex());
        }
        
        resizeAndRepaint();
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
		
		if (initialized)
			setPreferredColumnWidths();
	}

    protected boolean hasExcessWidth() {
    	
        return getPreferredSize().width < getParent().getWidth();
    }
    
	protected void setPreferredColumnWidths() {

		for (int column = 0; column < getColumnCount(); column++) {
			
			if (manuallyChangedColumuns.contains(column))
				continue;

			int width = MIN_COLUMN_WIDTH;
		
			for (int row = 0; row < getRowCount(); row++) {
				
				TableCellRenderer renderer = getCellRenderer(row, column);
				Component comp = prepareRenderer(renderer, row, column);
				width = Math.max(
						(int)(ENLARGE_COLUMN_WIDTH*(double)comp.getPreferredSize().width),
						width);
			}
			
			getColumnModel().getColumn(column).setPreferredWidth(width);
		}
	}
}