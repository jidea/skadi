/*
 * Copyright (c) 2014-2016 Jan Strauß <jan[at]over9000.eu>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package eu.over9000.skadi.ui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import eu.over9000.skadi.handler.ChatHandler;
import eu.over9000.skadi.handler.StreamHandler;
import eu.over9000.skadi.io.PersistenceHandler;
import eu.over9000.skadi.lock.LockWakeupReceiver;
import eu.over9000.skadi.lock.SingleInstanceLock;
import eu.over9000.skadi.model.Channel;
import eu.over9000.skadi.model.ChannelStore;
import eu.over9000.skadi.model.StateContainer;
import eu.over9000.skadi.model.StreamQuality;
import eu.over9000.skadi.service.ForcedChannelUpdateService;
import eu.over9000.skadi.service.ImportFollowedService;
import eu.over9000.skadi.service.LivestreamerVersionCheckService;
import eu.over9000.skadi.service.VersionCheckerService;
import eu.over9000.skadi.ui.cells.ChannelGridCell;
import eu.over9000.skadi.ui.cells.LiveCell;
import eu.over9000.skadi.ui.cells.RightAlignedCell;
import eu.over9000.skadi.ui.cells.UptimeCell;
import eu.over9000.skadi.ui.dialogs.SettingsDialog;
import eu.over9000.skadi.ui.dialogs.SyncDialog;
import eu.over9000.skadi.ui.tray.Tray;
import eu.over9000.skadi.util.*;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.*;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TableColumn.SortType;
import javafx.scene.image.Image;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.SegmentedButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Predicate;

public class MainWindow extends Application implements LockWakeupReceiver {

	public static final int TOOLBAR_HEIGHT = 32;
	private static final Logger LOGGER = LoggerFactory.getLogger(MainWindow.class);
	private final String darkCSS = getClass().getResource("/styles/dark.css").toExternalForm();
	private final StatusBarWrapper statusBarWrapper = new StatusBarWrapper();
	private ChannelStore channelStore;
	private ChatHandler chatHandler;
	private StreamHandler streamHandler;
	private PersistenceHandler persistenceHandler;
	private StateContainer applicationState;
	private ObjectProperty<Channel> detailChannel;
	private SplitPane splitPane;
	private ChannelDetailPane detailPane;
	private TableView<Channel> table;
	private ChannelGrid grid;
	private TableColumn<Channel, Boolean> liveCol;
	private TableColumn<Channel, String> nameCol;
	private TableColumn<Channel, String> titleCol;
	private TableColumn<Channel, String> gameCol;
	private TableColumn<Channel, Long> viewerCol;
	private TableColumn<Channel, Long> uptimeCol;
	private FilteredList<Channel> filteredChannelListTable;
	private FilteredList<Channel> filteredChannelListGrid;
	private Button add;
	private TextField addName;
	private Button details;
	private Button remove;
	private Button refresh;
	private ToggleButton onlineOnly;
	private ToolBar toolBarL;
	private ToolBar toolBarR;
	private TextField filterText;
	private HandlerControlButton chatAndStreamButton;
	private Stage stage;
	private Tray tray;
	private Scene scene;
	private Channel lastSelected;
	private Slider scaleSlider;

	private DoubleProperty scalingGridCellWidth;
	private DoubleProperty scalingGridCellHeight;
	private HBox sliderBox;
	private Button sync;

	@Override
	public void init() throws Exception {

		persistenceHandler = new PersistenceHandler();
		applicationState = persistenceHandler.loadState();

		TwitchUtil.init(applicationState.getAuthToken());

		channelStore = new ChannelStore(persistenceHandler, applicationState);
		chatHandler = new ChatHandler(applicationState);
		streamHandler = new StreamHandler(statusBarWrapper, channelStore, applicationState);

		detailChannel = new SimpleObjectProperty<>();
	}

	@Override
	public void start(final Stage stage) throws Exception {
		Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
			LOGGER.error("Uncaught exception in JavaFX Application Thread: ", throwable);
			LOGGER.error("Will exit");

			try {
				Thread.sleep(1000);
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}

			Platform.exit();
		});
		Platform.setImplicitExit(false);

		this.stage = stage;

		scaleSlider = new Slider(0.0, 1.0, applicationState.getGridScale());
		scaleSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
			applicationState.setGridScale(newValue.doubleValue());
			persistenceHandler.saveState(applicationState);
		});
		scalingGridCellWidth = new SimpleDoubleProperty();
		scalingGridCellHeight = new SimpleDoubleProperty();

		scalingGridCellWidth.bind(Bindings.createDoubleBinding(() -> NumberUtil.scale(scaleSlider.getValue(), 0.0, 1.0, 200, 500), scaleSlider.valueProperty()));
		scalingGridCellHeight.bind(Bindings.createDoubleBinding(() -> NumberUtil.scale(scaleSlider.getValue(), 0.0, 1.0, 200, 365), scaleSlider.valueProperty()));

		sliderBox = new HBox(scaleSlider);
		sliderBox.setAlignment(Pos.CENTER);

		detailPane = new ChannelDetailPane(this);

		final BorderPane borderPane = new BorderPane();
		splitPane = new SplitPane();
		splitPane.setBorder(Border.EMPTY);
		splitPane.setPadding(Insets.EMPTY);
		final StackPane stackPane = new StackPane();
		stackPane.setBorder(Border.EMPTY);

		stackPane.setPadding(Insets.EMPTY);

		setupTable();
		setupGrid();

		stackPane.getChildren().add(grid);
		stackPane.getChildren().add(table);

		setupToolbarLeft(stage);
		setupToolbarRight();

		splitPane.getItems().add(stackPane);

		final BorderPane toolbarPane = new BorderPane();

		toolbarPane.setCenter(toolBarL);
		toolbarPane.setRight(toolBarR);

		borderPane.setTop(toolbarPane);
		borderPane.setCenter(splitPane);

		borderPane.setBottom(statusBarWrapper.getStatusBar());

		scene = new Scene(borderPane);
		scene.getStylesheets().add(getClass().getResource("/styles/copyable-label.css").toExternalForm());
		scene.getStylesheets().add(getClass().getResource("/styles/common.css").toExternalForm());

		if (applicationState.isUseDarkTheme()) {
			scene.getStylesheets().add(darkCSS);
		}

		scene.setOnDragOver(event -> {
			final Dragboard d = event.getDragboard();
			if (d.hasUrl() || d.hasString()) {
				event.acceptTransferModes(TransferMode.COPY);
			} else {
				event.consume();
			}
		});

		scene.setOnDragDropped(event -> {
			final Dragboard d = event.getDragboard();
			boolean success = false;
			if (d.hasUrl()) {
				final String user = StringUtil.extractUsernameFromURL(d.getUrl());
				if (user != null) {
					success = channelStore.addChannel(user, statusBarWrapper);
				} else {
					statusBarWrapper.updateStatusText("dragged url is no twitch stream");
				}
			} else if (d.hasString()) {
				success = channelStore.addChannel(d.getString(), statusBarWrapper);
			}
			event.setDropCompleted(success);
			event.consume();

		});

		tray = new Tray(this);
		NotificationUtil.init(applicationState);

		restoreWindowState();

		stage.setTitle("Skadi");
		stage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/skadi.png")));
		stage.setScene(scene);
		stage.show();

		stage.iconifiedProperty().addListener((obs, oldV, newV) -> {
			if (applicationState.isMinimizeToTray()) {
				if (newV) {
					saveWindowState();
					stage.hide();
				}
			}
		});
		stage.setOnCloseRequest(event -> {
			saveWindowState();
			Platform.exit();
		});

		updateFilterPredicate();
		updateLiveColumn();
		bindColumnWidths();

		final VersionCheckerService versionCheckerService = new VersionCheckerService(stage, statusBarWrapper);
		versionCheckerService.start();

		final LivestreamerVersionCheckService livestreamerVersionCheckService = new LivestreamerVersionCheckService(statusBarWrapper, applicationState);
		livestreamerVersionCheckService.start();

		SingleInstanceLock.addReceiver(this);

	}

	public void showStage() {
		restoreWindowState();
		stage.show();
		stage.setIconified(false);
		stage.toFront();
	}

	private void saveWindowState() {
		applicationState.setWindowHeight(stage.getHeight());
		applicationState.setWindowWidth(stage.getWidth());
		persistenceHandler.saveState(applicationState);
	}

	private void restoreWindowState() {
		final double width = applicationState.getWindowWidth();
		final double height = applicationState.getWindowHeight();
		stage.setWidth(width);
		stage.setHeight(height);
	}

	private void setupGrid() {
		grid = new ChannelGrid();
		grid.setBorder(Border.EMPTY);
		grid.setPadding(Insets.EMPTY);
		grid.setCellFactory(gridView -> new ChannelGridCell(grid, this));
		grid.cellHeightProperty().bind(scalingGridCellHeight);
		grid.cellWidthProperty().bind(scalingGridCellWidth);
		grid.setHorizontalCellSpacing(5);
		grid.setVerticalCellSpacing(5);

		filteredChannelListGrid = new FilteredList<>(channelStore.getChannels());
		final SortedList<Channel> sortedChannelListGrid = new SortedList<>(filteredChannelListGrid);
		sortedChannelListGrid.setComparator((channel1, channel2) -> Long.compare(channel2.getViewer(), channel1.getViewer()));
		grid.setItems(sortedChannelListGrid);
	}

	private void setupToolbarRight() {
		final ToggleButton tbTable = GlyphsDude.createIconToggleButton(FontAwesomeIcon.TABLE, null, null, ContentDisplay.GRAPHIC_ONLY);
		final ToggleButton tbGrid = GlyphsDude.createIconToggleButton(FontAwesomeIcon.TH, null, null, ContentDisplay.GRAPHIC_ONLY);

		tbTable.setTooltip(new Tooltip("Table view"));
		tbGrid.setTooltip(new Tooltip("Grid view"));

		tbTable.setOnAction(event -> {
			table.toFront();
			applicationState.setShowGrid(false);
			persistenceHandler.saveState(applicationState);
			toggleScaleSlider(false);
		});

		tbGrid.setOnAction(event -> {
			grid.toFront();
			applicationState.setShowGrid(true);
			persistenceHandler.saveState(applicationState);
			toggleScaleSlider(true);
		});

		final SegmentedButton segmentedButton = new SegmentedButton(tbTable, tbGrid);
		final PersistentButtonToggleGroup toggleGroup = new PersistentButtonToggleGroup();
		segmentedButton.setToggleGroup(toggleGroup);
		toolBarR = new ToolBar(new Separator(), segmentedButton);
		toolBarR.setPrefHeight(TOOLBAR_HEIGHT);
		toolBarR.setMinHeight(TOOLBAR_HEIGHT);

		if (applicationState.isShowGrid()) {
			tbGrid.setSelected(true);
			grid.toFront();
			toggleScaleSlider(true);
		} else {
			tbTable.setSelected(true);
			table.toFront();
			toggleScaleSlider(false);
		}
	}

	private void toggleScaleSlider(final boolean visible) {
		if (visible) {
			statusBarWrapper.getStatusBar().getRightItems().add(sliderBox);
		} else {
			statusBarWrapper.getStatusBar().getRightItems().remove(sliderBox);
		}
	}

	@Override
	public void stop() throws Exception {
		super.stop();
		tray.onShutdown();
		ExecutorUtil.performShutdown();
		NotificationUtil.onShutdown();
	}

	private void setupToolbarLeft(final Stage stage) {

		add = GlyphsDude.createIconButton(FontAwesomeIcon.PLUS);
		addName = new TextField();
		addName.setOnAction(event -> add.fire());

		add.setOnAction(event -> {
			final String name = addName.getText().trim();

			if (name.isEmpty()) {
				return;
			}

			final String nameFromUrl = StringUtil.extractUsernameFromURL(name);

			final boolean result;
			if (nameFromUrl != null) {
				result = channelStore.addChannel(nameFromUrl, statusBarWrapper);
			} else {
				result = channelStore.addChannel(name, statusBarWrapper);
			}

			if (result) {
				addName.clear();
			}

		});

		final Button imprt = GlyphsDude.createIconButton(FontAwesomeIcon.DOWNLOAD);
		imprt.setOnAction(event -> {
			final TextInputDialog dialog = new TextInputDialog();
			dialog.initModality(Modality.APPLICATION_MODAL);
			dialog.initOwner(stage);
			dialog.setTitle("Import followed channels");
			dialog.setHeaderText("Import followed channels from Twitch");
			dialog.setGraphic(null);
			dialog.setContentText("Twitch username:");

			dialog.showAndWait().ifPresent(name -> {
				final ImportFollowedService ifs = new ImportFollowedService(channelStore, name, statusBarWrapper);
				ifs.start();
			});
		});

		sync = GlyphsDude.createIconButton(FontAwesomeIcon.COLUMNS);
		sync.setDisable(!applicationState.hasAuthCode());
		sync.setOnAction(event -> {

			final SyncDialog dialog = new SyncDialog(channelStore, statusBarWrapper);
			dialog.initModality(Modality.APPLICATION_MODAL);
			dialog.initOwner(stage);
			final Optional<Boolean> result = dialog.showAndWait();

			if (result.isPresent() && result.get()) {
				persistenceHandler.saveState(applicationState);
			}
		});

		details = GlyphsDude.createIconButton(FontAwesomeIcon.INFO);
		details.setDisable(true);
		details.setOnAction(event -> openDetailPage(lastSelected));
		details.setTooltip(new Tooltip("Show channel information"));

		remove = GlyphsDude.createIconButton(FontAwesomeIcon.TRASH);
		remove.setDisable(true);
		remove.setOnAction(event -> {
			final Channel candidate = lastSelected;

			final Alert alert = new Alert(AlertType.CONFIRMATION);
			alert.initModality(Modality.APPLICATION_MODAL);
			alert.initOwner(stage);
			alert.setTitle("Delete channel");
			alert.setHeaderText("Delete " + candidate.getName());
			alert.setContentText("Do you really want to delete " + candidate.getName() + "?");

			final Optional<ButtonType> result = alert.showAndWait();
			if (result.isPresent() && result.get() == ButtonType.OK) {
				channelStore.getChannels().remove(candidate);
				statusBarWrapper.updateStatusText("Removed channel " + candidate.getName());
			}
		});

		refresh = GlyphsDude.createIconButton(FontAwesomeIcon.REFRESH);
		refresh.setTooltip(new Tooltip("Refresh all channels"));
		refresh.setOnAction(event -> {
			refresh.setDisable(true);
			final ForcedChannelUpdateService service = new ForcedChannelUpdateService(channelStore, statusBarWrapper, refresh);
			service.start();
		});

		final Button settings = GlyphsDude.createIconButton(FontAwesomeIcon.COG);
		settings.setTooltip(new Tooltip("Settings"));
		settings.setOnAction(event -> {
			final SettingsDialog dialog = new SettingsDialog(applicationState, persistenceHandler);
			dialog.initModality(Modality.APPLICATION_MODAL);
			dialog.initOwner(stage);
			final Optional<StateContainer> result = dialog.showAndWait();
			if (result.isPresent()) {
				persistenceHandler.saveState(result.get());
				checkThemeChange();
				checkAuthChange();
			}
		});

		onlineOnly = new ToggleButton(null, GlyphsDude.createIcon(FontAwesomeIcon.VIDEO_CAMERA));
		onlineOnly.setSelected(applicationState.isOnlineFilterActive());
		onlineOnly.setTooltip(new Tooltip("Show only live channels"));

		onlineOnly.setOnAction(event -> {
			applicationState.setOnlineFilterActive(onlineOnly.isSelected());
			persistenceHandler.saveState(applicationState);
			updateFilterPredicate();
			updateLiveColumn();
		});

		filterText = new TextField();
		filterText.textProperty().addListener((obs, oldV, newV) -> updateFilterPredicate());
		filterText.setTooltip(new Tooltip("Filter channels by name, status and game"));

		toolBarL = new ToolBar();
		toolBarL.getItems().addAll(addName, add, imprt, sync, new Separator(), refresh, settings, new Separator(), onlineOnly, filterText, new Separator(), details, remove);
		toolBarL.setPrefHeight(TOOLBAR_HEIGHT);
		toolBarL.setMinHeight(TOOLBAR_HEIGHT);

		chatAndStreamButton = new HandlerControlButton(chatHandler, streamHandler, toolBarL, statusBarWrapper, applicationState);

	}

	private void updateLiveColumn() {
		if (onlineOnly.isSelected()) {
			table.getColumns().remove(liveCol);
			table.getSortOrder().remove(liveCol);
		} else {
			table.getColumns().add(0, liveCol);
			table.getSortOrder().add(0, liveCol);
		}
	}

	private void checkAuthChange() {
		final boolean hasAuth = applicationState.hasAuthCode();
		sync.setDisable(!hasAuth);
	}

	private void checkThemeChange() {
		final boolean useDark = applicationState.isUseDarkTheme();
		final boolean isPresent = scene.getStylesheets().contains(darkCSS);

		if (useDark == isPresent) {
			return;
		}

		if (useDark) {
			scene.getStylesheets().add(darkCSS);
		} else {
			scene.getStylesheets().remove(darkCSS);
		}

	}

	private void updateFilterPredicate() {
		final Predicate<Channel> channelPredicate = channel -> {

			final boolean isOnlineResult;
			final boolean containsTextResult;

			// isOnline returns a Boolean, can be null
			isOnlineResult = !onlineOnly.isSelected() || Boolean.TRUE.equals(channel.isOnline());

			final String filter = filterText.getText().trim();
			if (filter.isEmpty()) {
				containsTextResult = true;
			} else {
				final boolean nameContains = StringUtils.containsIgnoreCase(channel.getName(), filter);
				final boolean gameContains = StringUtils.containsIgnoreCase(channel.getGame(), filter);
				final boolean titleContains = StringUtils.containsIgnoreCase(channel.getTitle(), filter);
				containsTextResult = nameContains || gameContains || titleContains;
			}

			return isOnlineResult && containsTextResult;
		};

		filteredChannelListTable.setPredicate(channelPredicate);
		filteredChannelListGrid.setPredicate(channelPredicate);
	}

	private void setupTable() {
		table = new TableView<>();
		table.setBorder(Border.EMPTY);
		table.setPadding(Insets.EMPTY);

		liveCol = new TableColumn<>("Live");
		liveCol.setCellValueFactory(p -> p.getValue().onlineProperty());
		liveCol.setSortType(SortType.DESCENDING);
		liveCol.setCellFactory(p -> new LiveCell());

		nameCol = new TableColumn<>("Channel");
		nameCol.setCellValueFactory(p -> p.getValue().nameProperty());


		titleCol = new TableColumn<>("Status");
		titleCol.setCellValueFactory(p -> p.getValue().titleProperty());

		gameCol = new TableColumn<>("Game");
		gameCol.setCellValueFactory(p -> p.getValue().gameProperty());

		viewerCol = new TableColumn<>("Viewer");
		viewerCol.setCellValueFactory(p -> p.getValue().viewerProperty().asObject());
		viewerCol.setSortType(SortType.DESCENDING);
		viewerCol.setCellFactory(p -> new RightAlignedCell<>());

		uptimeCol = new TableColumn<>("Uptime");
		uptimeCol.setCellValueFactory(p -> p.getValue().uptimeProperty().asObject());
		uptimeCol.setCellFactory(p -> new UptimeCell());

		table.setPlaceholder(new Label("no channels added/matching the filters"));

		//table.getColumns().add(liveCol);
		table.getColumns().add(nameCol);
		table.getColumns().add(titleCol);
		table.getColumns().add(gameCol);
		table.getColumns().add(viewerCol);
		table.getColumns().add(uptimeCol);

		//table.getSortOrder().add(liveCol);
		table.getSortOrder().add(viewerCol);
		table.getSortOrder().add(nameCol);


		filteredChannelListTable = new FilteredList<>(channelStore.getChannels());
		final SortedList<Channel> sortedChannelListTable = new SortedList<>(filteredChannelListTable);
		sortedChannelListTable.comparatorProperty().bind(table.comparatorProperty());

		table.setItems(sortedChannelListTable);

		table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
			onSelection(newV);
			if ((newV == null) && splitPane.getItems().contains(detailPane)) {
				doDetailSlide(false);
			}
		});

		table.setOnMousePressed(event -> {
			if (table.getSelectionModel().getSelectedItem() == null) {
				return;
			}

			if (event.getButton() == MouseButton.MIDDLE) {
				openStream(table.getSelectionModel().getSelectedItem());

			} else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
				openDetailPage(table.getSelectionModel().getSelectedItem());
			}
		});
	}

	public void onSelection(final Channel channel) {
		details.setDisable(channel == null);
		remove.setDisable(channel == null);
		chatAndStreamButton.setDisable(channel == null);
		chatAndStreamButton.updateCandidate(channel);
		lastSelected = channel;
	}

	public void openDetailPage(final Channel channel) {
		if (channel == null) {
			return;
		}

		detailChannel.set(channel);
		if (!splitPane.getItems().contains(detailPane)) {
			splitPane.getItems().add(detailPane);
			doDetailSlide(true);
		}
	}

	private void bindColumnWidths() {
		final ScrollBar tsb = JavaFXUtil.getVerticalScrollbar(table);
		final ReadOnlyDoubleProperty sbw = tsb.widthProperty();
		final DoubleBinding tcw = table.widthProperty().subtract(sbw);

		liveCol.prefWidthProperty().bind(tcw.multiply(0.05));
		nameCol.prefWidthProperty().bind(tcw.multiply(0.15));
		titleCol.prefWidthProperty().bind(tcw.multiply(0.4));
		gameCol.prefWidthProperty().bind(tcw.multiply(0.2));
		viewerCol.prefWidthProperty().bind(tcw.multiply(0.075));
		uptimeCol.prefWidthProperty().bind(tcw.multiply(0.125));
	}

	public void doDetailSlide(final boolean doOpen) {

		final KeyValue positionKeyValue = new KeyValue(splitPane.getDividers().get(0).positionProperty(), doOpen ? 0.15 : 1);
		final KeyValue opacityKeyValue = new KeyValue(detailPane.opacityProperty(), doOpen ? 1 : 0);
		final KeyFrame keyFrame = new KeyFrame(Duration.seconds(0.1), positionKeyValue, opacityKeyValue);
		final Timeline timeline = new Timeline(keyFrame);
		timeline.setOnFinished(evt -> {
			if (!doOpen) {
				splitPane.getItems().remove(detailPane);
				detailPane.setOpacity(1);
			}
		});
		timeline.play();
	}

	public ObjectProperty<Channel> getDetailChannel() {
		return detailChannel;
	}

	@Override
	public void onWakeupReceived() {
		Platform.runLater(() -> {
			statusBarWrapper.updateStatusText("Wakeup received");
			showStage();
		});
	}

	public void openStream(final Channel item) {
		if (item == null) {
			return;
		}
		streamHandler.openStream(item, StreamQuality.getBestQuality());
	}


	public DoubleProperty scalingGridCellWidthProperty() {
		return scalingGridCellWidth;
	}

	public DoubleProperty scalingGridCellHeightProperty() {
		return scalingGridCellHeight;
	}
}
