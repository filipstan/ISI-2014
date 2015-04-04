import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import com.esri.runtime.ArcGISRuntime;
import com.esri.toolkit.JLayerList;
import com.esri.toolkit.sliders.JTimeSlider;
import com.esri.toolkit.sliders.JTimeSlider.TimeMode;
import com.esri.toolkit.utilities.ResultPanel;
import com.esri.map.FeatureLayer;
import com.esri.map.JMap;
import com.esri.map.Layer;
import com.esri.map.LayerInitializeCompleteEvent;
import com.esri.map.LayerInitializeCompleteListener;
import com.esri.map.LayerList;
import com.esri.map.MapEvent;
import com.esri.map.MapEventListener;
import com.esri.map.MapOptions;
import com.esri.map.MapOptions.MapType;
import com.esri.map.ArcGISTiledMapServiceLayer;
import com.esri.map.MapOverlay;
import com.esri.map.TimeAwareLayer;
import com.esri.client.local.ArcGISLocalDynamicMapServiceLayer;
import com.esri.client.local.ArcGISLocalTiledLayer;
import com.esri.core.geodatabase.ShapefileFeatureTable;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.Graphic;
import com.esri.core.map.TimeExtent;
import com.esri.core.map.TimeOptions.Units;
import com.esri.core.renderer.UniqueValueInfo;
import com.esri.core.renderer.UniqueValueRenderer;
import com.esri.core.symbol.PictureMarkerSymbol;
import com.esri.core.symbol.SimpleFillSymbol;
import com.esri.core.tasks.identify.IdentifyParameters;
import com.esri.core.tasks.identify.IdentifyResult;
import com.esri.core.tasks.identify.IdentifyTask;
import com.esri.map.GraphicsLayer;

public class StatiiMeteo {

	private JFrame window;
	private JMap map;
	private JProgressBar progressBar;
	private JTimeSlider timeSlider;
	private static String FSP = System.getProperty("file.separator");
	private static String ARCGIS_DATA_URL = "arcgis data\\";
	// private static String ARCGIS_SDK_JAVA_URL =
	// "C:\\Program Files\\ArcGIS SDKs\\java10.2.4\\";
	private FeatureLayer featureLayer;
	private ShapefileFeatureTable shapefileTable;
	private ArcGISLocalDynamicMapServiceLayer dynamicLayer;
	private ArcGISLocalDynamicMapServiceLayer dynamicLayer_judete;
	private JComponent contentPane;
	private ResultPanel resultPanel;
	private JLayerList jLayerList;

	// UI constants for sizing
	private static final int PANEL_WIDTH = 250;

	public StatiiMeteo() {
		window = new JFrame();
		window.setSize(960, 600);
		window.setLocationRelativeTo(null); // center on screen
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.getContentPane().setLayout(new BorderLayout(0, 0));

		JLayeredPane contentPane = new JLayeredPane();
		contentPane.setBounds(100, 100, 1000, 700);
		contentPane.setLayout(new BorderLayout(0, 0));
		contentPane.setVisible(true);

		// result panel
		resultPanel = new ResultPanel();
		resultPanel.setLocation(10, 10);
		window.add(contentPane);
		contentPane.add(resultPanel);
		window.setVisible(true);

		final JMap map = new JMap();
		jLayerList = new JLayerList(map);
		updateProgresBarUI("Starting local dynamic map service...", true);
		contentPane.add(map, BorderLayout.CENTER);
		// window.getContentPane().add(map, BorderLayout.CENTER);

		// dispose map just before application window is closed.
		window.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent windowEvent) {
				super.windowClosing(windowEvent);
				map.dispose();
			}
		});

		// set the initial extent of the map
		map.setExtent(new Envelope(2371147, 5256055, 3241260, 6257266));

		// setting a tiled layer as basemap
		final ArcGISTiledMapServiceLayer tiledLayer_online = new ArcGISTiledMapServiceLayer(
				"http://services.arcgisonline.com/ArcGIS/rest/services/NatGeo_World_Map/MapServer");

		final ArcGISLocalTiledLayer tiledLayer = new ArcGISLocalTiledLayer(
				ARCGIS_DATA_URL + "Topographic.tpk");
		tiledLayer.setName("Topologia lumii");
		tiledLayer_online.setName("Harta lumii");
		final LayerList layers = map.getLayers();
		layers.add(tiledLayer);
		layers.add(tiledLayer_online);
		tiledLayer.setVisible(false);
		tiledLayer_online.setVisible(false);

		// create and add the dynamic layer
		dynamicLayer = new ArcGISLocalDynamicMapServiceLayer(ARCGIS_DATA_URL
				+ "statiiv12.mpk");

		dynamicLayer.setName("Statii meteo România");
		dynamicLayer.setVisible(true);

		dynamicLayer_judete = new ArcGISLocalDynamicMapServiceLayer(
				ARCGIS_DATA_URL + "judete.mpk");

		dynamicLayer_judete.setName("Județele României");
		dynamicLayer_judete.setVisible(true);

		map.addMapOverlay(new MouseClickedOverlay(map));

		// create the time slider with the dynamic layer
		timeSlider = createTimeSlider(dynamicLayer);

		// set time extent of time slider once the dynamic layer is initialized
		dynamicLayer
				.addLayerInitializeCompleteListener(new LayerInitializeCompleteListener() {
					@Override
					public void layerInitializeComplete(
							LayerInitializeCompleteEvent arg0) {
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								timeSlider.setVisible(true);

								// description.setVisible(true);
								// legend.setVisible(true);
							}
						});
						synchronized (progressBar) {
							if (arg0.getID() == LayerInitializeCompleteEvent.LOCALLAYERCREATE_ERROR) {
								String errMsg = "Failed to initialize due to "
										+ dynamicLayer.getInitializationError();
								JOptionPane.showMessageDialog(map, errMsg, "",
										JOptionPane.ERROR_MESSAGE);
							}
							updateProgresBarUI(null, false);
						}

						// activate buttons once the dynamic layer is done
						// initializing
						// renderButton.setEnabled(true);
						// defaultButton.setEnabled(true);
					}
				});
		map.getLayers().add(dynamicLayer);

		dynamicLayer_judete
				.addLayerInitializeCompleteListener(new LayerInitializeCompleteListener() {

					@Override
					public void layerInitializeComplete(
							LayerInitializeCompleteEvent e) {
						synchronized (progressBar) {
							if (e.getID() == LayerInitializeCompleteEvent.LOCALLAYERCREATE_ERROR) {
								String errMsg = "Failed to initialize due to "
										+ dynamicLayer_judete
												.getInitializationError();
								JOptionPane.showMessageDialog(map, errMsg, "",
										JOptionPane.ERROR_MESSAGE);
							}
							updateProgresBarUI(null, false);
						}
					}
				});
		map.getLayers().add(dynamicLayer_judete);

		// System.out.println("i: " + dynamicLayer);
		// System.out.println("i: " + dynamicLayer_judete);

		contentPane.add(timeSlider, BorderLayout.SOUTH);

		// button - when clicked, focus on the U.S. extent
		JButton btnROExtent = new JButton("Focus on România");
		btnROExtent.setMaximumSize(new Dimension(200, 25));
		btnROExtent.setMinimumSize(new Dimension(200, 25));
		btnROExtent.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				map.setExtent(new Envelope(2371147, 5256055, 3241260, 6257266));
			}
		});
		btnROExtent.setAlignmentX(Component.CENTER_ALIGNMENT);

		// group the above UI items into a panel
		final JPanel controlPanel = new JPanel();
		BoxLayout boxLayout = new BoxLayout(controlPanel, BoxLayout.Y_AXIS);
		controlPanel.setLayout(boxLayout);
		controlPanel.setLocation(10, 10);
		controlPanel.setSize(10, 20);
		controlPanel.setBackground(new Color(0, 0, 0, 100));
		controlPanel.setBorder(new LineBorder(Color.BLACK, 3, false));

		controlPanel.add(btnROExtent);
		controlPanel.add(Box.createVerticalStrut(5));

		// System.out.println(map);
		// System.out.println(jLayerList);
		controlPanel.add(jLayerList);
		controlPanel.add(Box.createVerticalStrut(5));

		progressBar = createProgressBar(controlPanel);
		controlPanel.add(progressBar);

		contentPane.add(controlPanel, BorderLayout.EAST);

	}

	/**
	 * Creates a JTimeSlider.
	 * 
	 * @param TimeAwareLayer
	 *            layer to which the time slider will be associated.
	 */
	private JTimeSlider createTimeSlider(TimeAwareLayer layer) {
		JTimeSlider jTimeSlider = new JTimeSlider();
		jTimeSlider.setTitle("Stații meteo România");
		jTimeSlider.addLayer(layer);

		jTimeSlider.setTimeExtent(new TimeExtent(new GregorianCalendar(1960, 1,
				1), new GregorianCalendar(2015, 1, 1)), 1, Units.Years);

		jTimeSlider.setTimeMode(TimeMode.CumulativeFromStart);
		jTimeSlider.setPlaybackRate(500); // 0.5 second per tick
		jTimeSlider.setVisible(false);
		return jTimeSlider;
	}

	private void identify(Geometry geometry, JMap map) {
		IdentifyParameters identifyparam = new IdentifyParameters();
		identifyparam.setGeometry(geometry);
		identifyparam.setMapExtent(map.getExtent());
		identifyparam.setSpatialReference(map.getSpatialReference());
		identifyparam.setMapHeight(map.getHeight());
		identifyparam.setMapWidth(map.getWidth());
		identifyparam.setLayerMode(IdentifyParameters.VISIBLE_LAYERS);
		identifyparam.setDPI(ArcGISRuntime.getDPI());

		IdentifyTask task = new IdentifyTask(dynamicLayer.getUrl());
		IdentifyTask task_jud = new IdentifyTask(dynamicLayer_judete.getUrl());

		// System.out.println(dynamicLayer);
		// System.out.println(dynamicLayer_judete);

		try {
			IdentifyResult[] results = task.execute(identifyparam);
			IdentifyResult[] results_jud = task_jud.execute(identifyparam);

			displayResults(results, results_jud);
		} catch (Exception e) {
			e.printStackTrace();
			// JOptionPane.showMessageDialog(contentPane, e.getMessage());
		}
	}

	public IdentifyResult[] concat(IdentifyResult[] a, IdentifyResult[] b) {
		int aLen = a.length;
		int bLen = b.length;
		IdentifyResult[] c = new IdentifyResult[aLen + bLen];
		System.arraycopy(a, 0, c, 0, aLen);
		System.arraycopy(b, 0, c, aLen, bLen);
		return c;
	}

	private void displayResults(IdentifyResult[] results,
			IdentifyResult[] results_jud) {

		int tot_len = results.length + results_jud.length;

		// write the new title for our query results panel
		resultPanel.setTitle(tot_len + " rezultat" + (tot_len == 1 ? "" : "e")
				+ ":");

		// write the results to our text area
		StringBuilder text = new StringBuilder();

		for (int i = 0; i < results.length; i++) {
			IdentifyResult result = results[i];

			String resultString = "Nume statie: "
					+ result.getAttributes().get("NUME") + "\n";
			resultString += "Data apariției: "
					+ result.getAttributes().get("Date") + "\n";

			text.append(resultString + "\n");
		}

		for (int i = 0; i < results_jud.length; i++) {
			IdentifyResult result = results_jud[i];

			// System.out.println(result);

			String resultString = "Județ: "
					+ result.getAttributes().get("NAME") + "\n";

			text.append(resultString + "\n");
		}

		resultPanel.setContent(text.toString());
		resultPanel.setVisible(true);
	}

	private class MouseClickedOverlay extends MapOverlay {
		private static final long serialVersionUID = 1L;
		private JMap map;

		MouseClickedOverlay(JMap map_aux) {
			map = map_aux;
		}

		@Override
		public void onMouseClicked(MouseEvent arg0) {
			try {
				// System.out.println(map);
				Point mapPoint = map.toMapPoint(arg0.getX(), arg0.getY());
				identify(mapPoint, map);
			} finally {
				super.onMouseClicked(arg0);
			}
		}
	}

	/**
	 * Starting point of this application.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {

			@Override
			public void run() {
				try {

					// System.setProperty("user.dir", ARCGIS_SDK_JAVA_URL);

					StatiiMeteo application = new StatiiMeteo();
					application.window.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public JComponent createUI() {

		contentPane = createContentPane();

		// result panel
		resultPanel = new ResultPanel();
		resultPanel.setLocation(10, 10);

		contentPane.add(map);

		return contentPane;
	}

	private static JLayeredPane createContentPane() {
		JLayeredPane contentPane = new JLayeredPane();
		contentPane.setBounds(100, 100, 1000, 700);
		contentPane.setLayout(new BorderLayout(0, 0));
		contentPane.setVisible(true);
		return contentPane;
	}

	private static JProgressBar createProgressBar(final JComponent parent) {
		final JProgressBar progressBar = new JProgressBar();
		progressBar.setSize(280, 20);
		parent.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				progressBar.setLocation(
						parent.getWidth() / 2 - progressBar.getWidth() / 2,
						parent.getHeight() - progressBar.getHeight() - 20);
			}
		});
		progressBar.setStringPainted(true);
		progressBar.setIndeterminate(true);
		return progressBar;
	}

	private void updateProgresBarUI(final String str, final boolean visible) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (str != null) {
					progressBar.setString(str);
				}
				progressBar.setVisible(visible);
			}
		});
	}
}
