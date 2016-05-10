package bdv.bigcat.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import net.imglib2.RealPoint;
import bdv.bigcat.annotation.Annotation;
import bdv.bigcat.annotation.Annotations;
import bdv.bigcat.annotation.PostSynapticSite;
import bdv.bigcat.annotation.PreSynapticSite;
import bdv.bigcat.annotation.Synapse;
import bdv.bigcat.control.AnnotationsController;
import bdv.bigcat.util.Selection;

public class AnnotationsWindow extends JFrame implements
		Selection.SelectionListener<Annotation>, ListSelectionListener {

	private static final long serialVersionUID = 1L;

	private final AnnotationsController annotationController;
	private final Annotations annotations;
	private final Selection<Annotation> selection;
	private BigCatTable table;
	private AnnotationsTableModel tableModel;

	private final GridBagLayout gridbag = new GridBagLayout();
	private final GridBagConstraints gridbagConstraints = new GridBagConstraints();

	private Boolean editingSelection = false;

	class AnnotationsTableModel extends AbstractTableModel implements
			Annotations.AnnotationsListener {

		private static final long serialVersionUID = 1L;

		public static final int ID_INDEX = 0;
		public static final int TYPE_INDEX = 1;
		public static final int LOCATION_X_INDEX = 2;
		public static final int LOCATION_Y_INDEX = 3;
		public static final int LOCATION_Z_INDEX = 4;
		public static final int COMMENT_INDEX = 5;
		public final String[] ColumnNames = { "id", "type", "x", "y", "z",
				"comment" };

		public List<Long> ids;
		public Map<Long, Integer> idsToRow = new HashMap<Long, Integer>();

		public AnnotationsTableModel() {

			annotations.addAnnotationsListener(this);
			ids = new LinkedList<Long>();
			for (Annotation a : annotations.getAnnotations())
				ids.add(a.getId());
			updateTableFromIds();
		}

		public long getIdFromRow(int row) {

			return ids.get(row);
		}

		public int getRowFromId(long id) {

			return idsToRow.get(id);
		}

		public String getColumnName(int column) {

			return ColumnNames[column];
		}

		public boolean isCellEditable(int row, int column) {

			if (column == COMMENT_INDEX)
				return true;
			return false;
		}

		public Class<?> getColumnClass(int column) {

			switch (column) {

			case ID_INDEX:
				return Long.class;
			case TYPE_INDEX:
				return String.class;
			case LOCATION_X_INDEX:
			case LOCATION_Y_INDEX:
			case LOCATION_Z_INDEX:
				return Integer.class;
			case COMMENT_INDEX:
				return String.class;
			default:
				return Object.class;
			}
		}

		public Object getValueAt(int row, int column) {

			Long id = ids.get(row);

			if (column == ID_INDEX)
				return id;

			Annotation a = annotations.getById(id);

			switch (column) {

			case TYPE_INDEX:
				return toTypeString(a);
			case LOCATION_X_INDEX:
				return (int) Math.round(a.getPosition().getFloatPosition(0));
			case LOCATION_Y_INDEX:
				return (int) Math.round(a.getPosition().getFloatPosition(1));
			case LOCATION_Z_INDEX:
				return (int) Math.round(a.getPosition().getFloatPosition(2));
			case COMMENT_INDEX:
				return a.getComment();
			default:
				return new Object();
			}
		}

		public void setValueAt(Object value, int row, int column) {

			if (column != COMMENT_INDEX)
				return;

			Long id = ids.get(row);
			Annotation a = annotations.getById(id);

			a.setComment((String) value);

			fireTableCellUpdated(row, column);
		}

		public int getRowCount() {

			return ids.size();
		}

		public int getColumnCount() {

			return ColumnNames.length;
		}

		@Override
		public void onAnnotationAdded(Annotation a) {

			ids.add(a.getId());
			updateTableFromIds();
		}

		@Override
		public void onAnnotationRemoved(Annotation a) {

			ids.remove(a.getId());
			updateTableFromIds();
		}

		private void updateTableFromIds() {

			int row = 0;
			idsToRow.clear();
			for (long id : ids) {
				idsToRow.put(id, row);
				row++;
			}

			fireTableDataChanged();
		}
	}

	public AnnotationsWindow(AnnotationsController annotationController,
			Annotations annotations, Selection<Annotation> selection) {

		this.annotationController = annotationController;
		this.annotations = annotations;
		this.selection = selection;
		selection.addSelectionListener(this);

		getContentPane().setLayout(gridbag);

		JScrollPane scrollPane = createAnnotationTable();
		gridbagConstraints.fill = GridBagConstraints.BOTH;
		gridbagConstraints.weightx = 1.0;
		gridbagConstraints.weighty = 1.0;
		gridbag.setConstraints(scrollPane, gridbagConstraints);
		getContentPane().add(scrollPane);

		JPanel stats = createTableStats();
		gridbagConstraints.gridy = 1;
		gridbagConstraints.weighty = 0.0;
		gridbag.setConstraints(stats, gridbagConstraints);
		getContentPane().add(stats);
		
		JPanel localizer = createLocalizer();
		gridbagConstraints.gridy = 2;
		gridbagConstraints.weighty = 0.0;
		gridbag.setConstraints(localizer, gridbagConstraints);
		getContentPane().add(localizer);
				
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		pack();
		setSize(500, 800);
	}

	private JScrollPane createAnnotationTable() {
		tableModel = new AnnotationsTableModel();
		table = new BigCatTable(tableModel);
		table.getSelectionModel().addListSelectionListener(this);
		JScrollPane scrollPane = new JScrollPane(table);
		return scrollPane;
	}

	private JPanel createTableStats() {
		
		JPanel panel = new JPanel();
		GridBagLayout gridBag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();

		panel.setLayout(gridBag);
	
		JLabel numItemsLabel = new JLabel("total: ");
		c.gridx = 0;
		c.gridy = 0;
		gridBag.setConstraints(numItemsLabel, c);
		panel.add(numItemsLabel);
		
		class NumItems extends JLabel implements TableModelListener {

			private static final long serialVersionUID = 1L;
			private final TableModel tableModel;

			public NumItems(TableModel model) {
				tableModel = model;
				tableModel.addTableModelListener(this);
				setText(Integer.toString(tableModel.getRowCount()));
			}
			
			@Override
			public void tableChanged(TableModelEvent e) {
				setText(Integer.toString(tableModel.getRowCount()));
			}
		};
		NumItems numItems = new NumItems(tableModel);
		c.gridx = 1;
		gridBag.setConstraints(numItems, c);
		panel.add(numItems);

		return panel;
	}
	
	private JPanel createLocalizer() {

		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Localize"),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));

		GridBagLayout gridBag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		SpinnerNumberModel fieldXValue = new SpinnerNumberModel(0.0,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.1);
		SpinnerNumberModel fieldYValue = new SpinnerNumberModel(0.0,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.1);
		SpinnerNumberModel fieldZValue = new SpinnerNumberModel(0.0,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.1);
		SpinnerNumberModel fieldFovValue = new SpinnerNumberModel(100.0, 0.1,
				Double.POSITIVE_INFINITY, 1.0);
		JSpinner fieldX = new JSpinner(fieldXValue);
		JSpinner fieldY = new JSpinner(fieldYValue);
		JSpinner fieldZ = new JSpinner(fieldZValue);
		JSpinner fieldFov = new JSpinner(fieldFovValue);
		JButton buttonGo = new JButton("Go");
		JLabel labelX = new JLabel(" x: ");
		JLabel labelY = new JLabel(" y: ");
		JLabel labelZ = new JLabel(" z: ");
		JLabel labelFov = new JLabel("fov: ");

		panel.setLayout(gridBag);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 0.0;
		gridBag.setConstraints(labelX, c);
		gridBag.setConstraints(labelY, c);
		gridBag.setConstraints(labelZ, c);
		c.weightx = 1.0;
		gridBag.setConstraints(fieldX, c);
		gridBag.setConstraints(fieldY, c);
		gridBag.setConstraints(fieldZ, c);
		panel.add(labelX);
		panel.add(fieldX);
		panel.add(labelY);
		panel.add(fieldY);
		panel.add(labelZ);
		panel.add(fieldZ);
		c.gridy = 1;
		c.weightx = 0.0;
		gridBag.setConstraints(labelFov, c);
		c.weightx = 1.0;
		gridBag.setConstraints(fieldFov, c);
		c.gridwidth = 3;
		c.gridx = 3;
		gridBag.setConstraints(buttonGo, c);
		panel.add(labelFov);
		panel.add(fieldFov);
		panel.add(buttonGo);

		buttonGo.addActionListener(new LocalizeAction(fieldXValue, fieldYValue,
				fieldZValue, fieldFovValue));

		return panel;
	}

	private String toTypeString(Annotation a) {

		if (a instanceof Synapse)
			return "synapse";
		if (a instanceof PreSynapticSite)
			return "presynaptic_site";
		if (a instanceof PostSynapticSite)
			return "postsynaptic_site";
		throw new RuntimeException("unknown annotation class " + a.getClass());
	}

	@Override
	public void itemSelected(Annotation t) {

		synchronized (editingSelection) {

			if (editingSelection)
				return;

			int row = tableModel.getRowFromId(t.getId());
			table.addRowSelectionInterval(row, row);
		}
	}

	@Override
	public void itemUnselected(Annotation t) {

		synchronized (editingSelection) {

			if (editingSelection)
				return;

			int row = tableModel.getRowFromId(t.getId());
			table.removeRowSelectionInterval(row, row);
		}
	}

	@Override
	public void selectionCleared() {

		synchronized (editingSelection) {

			if (editingSelection || table.getRowCount() == 0)
				return;

			table.removeRowSelectionInterval(0, table.getRowCount() - 1);
		}
	}

	@Override
	public void valueChanged(ListSelectionEvent event) {

		synchronized (editingSelection) {

			editingSelection = true;
			for (int row = event.getFirstIndex(); row <= event.getLastIndex(); row++)
				if (table.isRowSelected(row))
					selection.add(itemFromRow(row));
				else
					selection.remove(itemFromRow(row));
			editingSelection = false;
		}
	}

	private Annotation itemFromRow(int row) {

		if (row < 0 || row >= tableModel.getRowCount())
			return null;
		return annotations.getById(tableModel.getIdFromRow(row));
	}

	private class LocalizeAction implements ActionListener {

		final SpinnerNumberModel xValue;
		final SpinnerNumberModel yValue;
		final SpinnerNumberModel zValue;
		final SpinnerNumberModel fovValue;

		public LocalizeAction(SpinnerNumberModel xValue,
				SpinnerNumberModel yValue, SpinnerNumberModel zValue,
				SpinnerNumberModel fovValue) {

			this.xValue = xValue;
			this.yValue = yValue;
			this.zValue = zValue;
			this.fovValue = fovValue;
		}

		@Override
		public void actionPerformed(ActionEvent arg0) {

			double x = (double) xValue.getNumber();
			double y = (double) yValue.getNumber();
			double z = (double) zValue.getNumber();
			double fov = (double) fovValue.getNumber();

			annotationController.goTo(new RealPoint(x, y, z));
			annotationController.setFov(fov);
		}
	}
}
