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

import bdv.bigcat.annotation.Annotation;
import bdv.bigcat.annotation.Annotations;
import bdv.bigcat.annotation.PostSynapticSite;
import bdv.bigcat.annotation.PreSynapticSite;
import bdv.bigcat.annotation.Synapse;
import bdv.bigcat.control.AnnotationsController;
import bdv.bigcat.util.Selection;
import net.imglib2.RealPoint;

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
	private final Object lock = new Object();

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
			for (final Annotation a : annotations.getAnnotations())
				ids.add(a.getId());
			updateTableFromIds();
		}

		public long getIdFromRow(final int row) {

			return ids.get(row);
		}

		public int getRowFromId(final long id) {

			return idsToRow.get(id);
		}

		@Override
		public String getColumnName(final int column) {

			return ColumnNames[column];
		}

		@Override
		public boolean isCellEditable(final int row, final int column) {

			if (column == COMMENT_INDEX)
				return true;
			return false;
		}

		@Override
		public Class<?> getColumnClass(final int column) {

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

		@Override
		public Object getValueAt(final int row, final int column) {

			final Long id = ids.get(row);

			if (column == ID_INDEX)
				return id;

			final Annotation a = annotations.getById(id);

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

		@Override
		public void setValueAt(final Object value, final int row, final int column) {

			if (column != COMMENT_INDEX)
				return;

			final Long id = ids.get(row);
			final Annotation a = annotations.getById(id);

			a.setComment((String) value);

			fireTableCellUpdated(row, column);
		}

		@Override
		public int getRowCount() {

			return ids.size();
		}

		@Override
		public int getColumnCount() {

			return ColumnNames.length;
		}

		@Override
		public void onAnnotationAdded(final Annotation a) {

			ids.add(a.getId());
			updateTableFromIds();
		}

		@Override
		public void onAnnotationRemoved(final Annotation a) {

			ids.remove(a.getId());
			updateTableFromIds();
		}

		private void updateTableFromIds() {

			int row = 0;
			idsToRow.clear();
			for (final long id : ids) {
				idsToRow.put(id, row);
				row++;
			}

			fireTableDataChanged();
		}
	}

	public AnnotationsWindow(final AnnotationsController annotationController,
			final Annotations annotations, final Selection<Annotation> selection) {

		this.annotationController = annotationController;
		this.annotations = annotations;
		this.selection = selection;
		selection.addSelectionListener(this);

		getContentPane().setLayout(gridbag);

		final JScrollPane scrollPane = createAnnotationTable();
		gridbagConstraints.fill = GridBagConstraints.BOTH;
		gridbagConstraints.weightx = 1.0;
		gridbagConstraints.weighty = 1.0;
		gridbag.setConstraints(scrollPane, gridbagConstraints);
		getContentPane().add(scrollPane);

		final JPanel stats = createTableStats();
		gridbagConstraints.gridy = 1;
		gridbagConstraints.weighty = 0.0;
		gridbag.setConstraints(stats, gridbagConstraints);
		getContentPane().add(stats);

		final JPanel localizer = createLocalizer();
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
		final JScrollPane scrollPane = new JScrollPane(table);
		return scrollPane;
	}

	private JPanel createTableStats() {

		final JPanel panel = new JPanel();
		final GridBagLayout gridBag = new GridBagLayout();
		final GridBagConstraints c = new GridBagConstraints();

		panel.setLayout(gridBag);

		final JLabel numItemsLabel = new JLabel("total: ");
		c.gridx = 0;
		c.gridy = 0;
		gridBag.setConstraints(numItemsLabel, c);
		panel.add(numItemsLabel);

		class NumItems extends JLabel implements TableModelListener {

			private static final long serialVersionUID = 1L;
			private final TableModel tableModel;

			public NumItems(final TableModel model) {
				tableModel = model;
				tableModel.addTableModelListener(this);
				setText(Integer.toString(tableModel.getRowCount()));
			}

			@Override
			public void tableChanged(final TableModelEvent e) {
				setText(Integer.toString(tableModel.getRowCount()));
			}
		};

		final NumItems numItems = new NumItems(tableModel);
		c.gridx = 1;
		gridBag.setConstraints(numItems, c);
		panel.add(numItems);

		return panel;
	}

	private JPanel createLocalizer() {

		final JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Localize"),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));

		final GridBagLayout gridBag = new GridBagLayout();
		final GridBagConstraints c = new GridBagConstraints();
		final SpinnerNumberModel fieldXValue = new SpinnerNumberModel(0.0,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.1);
		final SpinnerNumberModel fieldYValue = new SpinnerNumberModel(0.0,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.1);
		final SpinnerNumberModel fieldZValue = new SpinnerNumberModel(0.0,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.1);
		final SpinnerNumberModel fieldFovValue = new SpinnerNumberModel(100.0, 0.1,
				Double.POSITIVE_INFINITY, 1.0);
		final JSpinner fieldX = new JSpinner(fieldXValue);
		final JSpinner fieldY = new JSpinner(fieldYValue);
		final JSpinner fieldZ = new JSpinner(fieldZValue);
		final JSpinner fieldFov = new JSpinner(fieldFovValue);
		final JButton buttonGo = new JButton("Go");
		final JLabel labelX = new JLabel(" x: ");
		final JLabel labelY = new JLabel(" y: ");
		final JLabel labelZ = new JLabel(" z: ");
		final JLabel labelFov = new JLabel("fov: ");

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

	private String toTypeString(final Annotation a) {

		if (a instanceof Synapse)
			return "synapse";
		if (a instanceof PreSynapticSite)
			return "presynaptic_site";
		if (a instanceof PostSynapticSite)
			return "postsynaptic_site";
		throw new RuntimeException("unknown annotation class " + a.getClass());
	}

	@Override
	public void itemSelected(final Annotation t) {

		synchronized (lock) {
			if (editingSelection)
				return;

			final int row = tableModel.getRowFromId(t.getId());
			table.addRowSelectionInterval(row, row);
		}
	}

	@Override
	public void itemUnselected(final Annotation t) {
		System.out.println("item unselected");

		synchronized (lock) {		
			if (editingSelection)
				return;

			final int row = tableModel.getRowFromId(t.getId());
			table.removeRowSelectionInterval(row, row);
		}
	}

	@Override
	public void selectionCleared() {

		synchronized (lock) {
			if (editingSelection || table.getRowCount() == 0)
				return;

			table.removeRowSelectionInterval(0, table.getRowCount() - 1);
		}
	}

	@Override
	public void valueChanged(final ListSelectionEvent event) {

		synchronized (lock) {
			editingSelection = true;
			for (int row = event.getFirstIndex(); row <= event.getLastIndex(); row++)
				if (table.isRowSelected(row))
					selection.add(itemFromRow(row));
				else
					selection.remove(itemFromRow(row));
			editingSelection = false;
		}
	}

	private Annotation itemFromRow(final int row) {

		if (row < 0 || row >= tableModel.getRowCount())
			return null;
		return annotations.getById(tableModel.getIdFromRow(row));
	}

	private class LocalizeAction implements ActionListener {

		final SpinnerNumberModel xValue;
		final SpinnerNumberModel yValue;
		final SpinnerNumberModel zValue;
		final SpinnerNumberModel fovValue;

		public LocalizeAction(final SpinnerNumberModel xValue,
				final SpinnerNumberModel yValue, final SpinnerNumberModel zValue,
				final SpinnerNumberModel fovValue) {

			this.xValue = xValue;
			this.yValue = yValue;
			this.zValue = zValue;
			this.fovValue = fovValue;
		}

		@Override
		public void actionPerformed(final ActionEvent arg0) {

			final double x = (double) xValue.getNumber();
			final double y = (double) yValue.getNumber();
			final double z = (double) zValue.getNumber();
			final double fov = (double) fovValue.getNumber();

			annotationController.goTo(new RealPoint(x, y, z));
			annotationController.setFov(fov);
		}
	}
}
