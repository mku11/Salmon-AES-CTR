package com.mku.salmon.vault.controller;
/*
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import com.mku.file.JavaDrive;
import com.mku.salmon.vault.dialog.SalmonDialog;
import com.mku.salmon.vault.dialog.SalmonDialogs;
import com.mku.salmon.vault.model.SalmonVaultManager;
import com.mku.salmon.vault.model.win.SalmonWinVaultManager;
import com.mku.salmon.vault.services.*;
import com.mku.salmon.vault.utils.WindowUtils;
import com.mku.salmon.vault.viewmodel.SalmonFileViewModel;
import com.mku.salmonfs.SalmonAuthException;
import com.mku.salmonfs.SalmonDriveManager;
import com.mku.salmonfs.SalmonFile;
import com.mku.utils.SalmonFileUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.MenuItem;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;

import java.awt.*;
import java.io.IOException;
import java.util.stream.Collectors;

public class MainController {
    private static final long MAX_TEXT_FILE = 1 * 1024 * 1024;
    @FXML
    public final ObservableList<SalmonFileViewModel> fileItemList = FXCollections.observableArrayList();

    @FXML
    public TableView<SalmonFileViewModel> table;
    private Stage stage;

    @FXML
    private final SimpleStringProperty status = new SimpleStringProperty();

    @FXML
    public SimpleStringProperty statusProperty() {
        return status;
    }

    public String getStatus() {
        return status.get();
    }

    @FXML
    private final SimpleStringProperty path = new SimpleStringProperty();

    @FXML
    public SimpleStringProperty pathProperty() {
        return path;
    }

    public String getPath() {
        return path.get();
    }

    public void setPath(String value) {
        if (value.startsWith("/"))
            value = value.substring(1);
        path.set("salmonfs://" + value);
    }

    private final SimpleBooleanProperty progressVisibility = new SimpleBooleanProperty();

    @FXML
    public SimpleBooleanProperty progressVisibilityProperty() {
        return progressVisibility;
    }

    public boolean getProgressVisibility() {
        return progressVisibility.get();
    }

    @FXML
    private final SimpleDoubleProperty fileprogress = new SimpleDoubleProperty();

    @FXML
    public SimpleDoubleProperty fileprogressProperty() {
        return fileprogress;
    }

    public Double getFileprogress() {
        return fileprogress.get();
    }

    @FXML
    private final SimpleStringProperty fileprogresstext = new SimpleStringProperty();

    @FXML
    public SimpleStringProperty fileprogresstextProperty() {
        return fileprogresstext;
    }

    public String getFileprogresstext() {
        return fileprogresstext.get();
    }

    @FXML
    private final SimpleDoubleProperty filesprogress = new SimpleDoubleProperty();

    @FXML
    public SimpleDoubleProperty filesprogressProperty() {
        return filesprogress;
    }

    public Double getFilesprogress() {
        return filesprogress.get();
    }

    @FXML
    private final SimpleStringProperty filesprogresstext = new SimpleStringProperty();

    @FXML
    public SimpleStringProperty filesprogresstextProperty() {
        return filesprogresstext;
    }

    public String getFilesprogresstext() {
        return filesprogresstext.get();
    }

    private SalmonVaultManager manager;

    public MainController() {

    }

    private void fileItemAdded(Integer position, SalmonFile file) {
        WindowUtils.runOnMainThread(() ->
        {
            fileItemList.add(position, new SalmonFileViewModel(file));
        });
    }

    private void updateListItem(SalmonFile file) {
        SalmonFileViewModel vm = getViewModel(file);
        vm.update();
    }

    private void managerPropertyChanged(Object owner, String propertyName) {
        if (propertyName.equals("FileItemList")) {
            updateFileViewModels();
        } else if (propertyName.equals("CurrentItem")) {
            selectItem(manager.getCurrentItem());
        } else if (propertyName.equals("Status")) {
            WindowUtils.runOnMainThread(() -> status.setValue(manager.getStatus()));
        } else if (propertyName == "IsJobRunning") {
            WindowUtils.runOnMainThread(() ->
            {
                if (manager.getFileManagerMode() != SalmonVaultManager.Mode.Search) {
                    progressVisibility.setValue(manager.isJobRunning());
                }
                if (!manager.isJobRunning())
                    status.setValue("");
            }, manager.isJobRunning() ? 0 : 1000);
        } else if (propertyName.equals("Path")) WindowUtils.runOnMainThread(() -> path.set(manager.getPath()));
        else if (propertyName.equals("FileProgress")) {
            WindowUtils.runOnMainThread(() -> fileprogress.set(manager.getFileProgress()));
            WindowUtils.runOnMainThread(() -> fileprogresstext.set((int) (manager.getFileProgress() * 100f) + " %"));
        } else if (propertyName.equals("FilesProgress")) {
            WindowUtils.runOnMainThread(() -> filesprogress.set(manager.getFilesProgress()));
            WindowUtils.runOnMainThread(() -> filesprogresstext.set((int) (manager.getFilesProgress() * 100f) + " %"));
        }
    }

    private void updateFileViewModels() {
        if (manager.getFileItemList() == null)
            fileItemList.clear();
        else {
            fileItemList.clear();
            fileItemList.addAll(manager.getFileItemList().stream()
                    .map(SalmonFileViewModel::new)
                    .collect(Collectors.toList()));
        }
    }

    synchronized void onSelectedItems(java.util.List<SalmonFileViewModel> selectedItems) {
        manager.getSelectedFiles().clear();
        for (SalmonFileViewModel item : selectedItems) {
            manager.getSelectedFiles().add(item.getSalmonFile());
        }
    }

    @FXML
    private void initialize() {
        setupTable();
    }

    @SuppressWarnings("unchecked")
    private void setupTable() {
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setItems(fileItemList);
        table.setRowFactory(tv -> {
            TableRow<SalmonFileViewModel> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && (!row.isEmpty())) {
                    TableView.TableViewSelectionModel<SalmonFileViewModel> rowData = table.getSelectionModel();
                    onOpenItem(rowData.getSelectedIndex());
                } else if (event.getButton() == MouseButton.SECONDARY) {
                    ObservableList<SalmonFileViewModel> items = table.getSelectionModel().getSelectedItems();
                    openContextMenu(items);
                }
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                event.consume();
                TableView.TableViewSelectionModel<SalmonFileViewModel> rowData = table.getSelectionModel();
                onOpenItem(rowData.getSelectedIndex());
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            onSelectedItems(table.getSelectionModel().getSelectedItems());
        });
        Platform.runLater(() -> table.requestFocus());
    }

    public void onAbout() {
        SalmonDialogs.promptAbout();
    }

    public void onOpenVault() {
        SalmonDialogs.promptOpenVault();
    }

    public void onCreateVault() {
        SalmonDialogs.promptCreateVault();
    }

    private void onOpenItem(int selectedItem) {
        try {
            openItem(selectedItem);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onShow() {
        WindowUtils.runOnMainThread(() ->
        {
            manager.initialize();
        }, 1000);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        setupSalmonManager();
        stage.setOnCloseRequest(event -> {
            stage.close();
            Platform.exit();
            System.exit(0);
        });

        stage.setOnShowing(event -> Platform.runLater(() -> onShow()));
    }

    public void onRefresh() {
        manager.refresh();
    }

    public void onImport() {
        SalmonDialogs.promptImportFiles();
    }

    public void onExport() {
        try {
            manager.exportSelectedFiles(false);
        } catch (SalmonAuthException e) {
            SalmonDialog.promptDialog("Error", "Could not export files: " + e);
        }
    }

    public void onExportAndDelete() {
        try {
            manager.exportSelectedFiles(true);
        } catch (SalmonAuthException e) {
            SalmonDialog.promptDialog("Error", "Could not export and delete files: + e");
        }
    }

    public void onNewFolder() {
        SalmonDialogs.promptNewFolder();
    }

    public void onCopy() {
        if (!table.isFocused())
            return;
        manager.copySelectedFiles();
    }

    public void onCut() {
        if (!table.isFocused())
            return;
        manager.cutSelectedFiles();
    }

    public void onDelete() {
        if (!table.isFocused())
            return;
        SalmonDialogs.promptDelete();
    }

    public void onPaste() {
        if (!table.isFocused())
            return;
        manager.pasteSelected();
    }

    public void onSearch() {
        SalmonDialogs.promptSearch();
    }

    public void onStop() {
        manager.stopOperation();
    }

    public void onSettings() {
        try {
            SettingsController.openSettings(stage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onCloseVault() {
        manager.closeVault();
    }

    public void onChangePassword() {
        SalmonDialogs.promptChangePassword();
    }

    public void onImportAuth() {
        SalmonDialogs.promptImportAuth();
    }

    public void onExportAuth() {
        SalmonDialogs.promptExportAuth();
    }

    public void onRevokeAuth() {
        SalmonDialogs.promptRevokeAuth();
    }

    public void onDisplayAuthID() {
        SalmonDialogs.onDisplayAuthID();
    }

    public void onExit() {
        SalmonDialogs.promptExit();
    }

    public void onBack() {
        manager.goBack();
    }

    private void selectItem(SalmonFile file) {
        SalmonFileViewModel vm = getViewModel(file);
        if (vm == null)
            return;
        try {
            int index = 0;
            for (SalmonFileViewModel viewModel : fileItemList) {
                if (viewModel == vm) {
                    int finalIndex = index;
                    WindowUtils.runOnMainThread(() -> {
                        try {
                            table.getSelectionModel().select(finalIndex);
                            table.scrollTo(table.selectionModelProperty().get().getSelectedIndex());
                            table.requestFocus();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                    break;
                }
                index++;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void setupSalmonManager() {
        try {
            SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
            ServiceLocator.getInstance().register(ISettingsService.class, new JavaFxSettingsService());
            ServiceLocator.getInstance().register(IFileService.class, new JavaFxFileService());
            ServiceLocator.getInstance().register(IFileDialogService.class, new JavaFxFileDialogService(stage));
            ServiceLocator.getInstance().register(IWebBrowserService.class, new JavaFxBrowserService());
            ServiceLocator.getInstance().register(IKeyboardService.class, new JavaFxKeyboardService());
            ServiceLocator.getInstance().register(IMediaPlayerService.class, new JavaFxMediaPlayerService());

            if (System.getProperty("os.name").toUpperCase().toUpperCase().startsWith("WINDOWS"))
                manager = SalmonWinVaultManager.getInstance();
            else
                manager = SalmonVaultManager.getInstance();
            manager.openListItem = this::OpenListItem;
            manager.observePropertyChanges(this::managerPropertyChanged);
            manager.updateListItem = this::updateListItem;
            manager.onFileItemAdded = this::fileItemAdded;

        } catch (Exception e) {
            e.printStackTrace();
            new SalmonDialog(Alert.AlertType.ERROR, "Error during initializing: " + e.getMessage()).show();
        }
    }

    private void openContextMenu(ObservableList<SalmonFileViewModel> items) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem item = new MenuItem("View");
        item.setOnAction((event) -> onOpenItem(table.getSelectionModel().getSelectedIndex()));
        contextMenu.getItems().add(item);

        item = new MenuItem("View as Text");
        item.setOnAction((event) -> startTextEditor(items.get(0)));
        contextMenu.getItems().add(item);

        item = new MenuItem("Copy (Ctrl+C)");
        item.setOnAction((event) -> onCopy());
        contextMenu.getItems().add(item);

        item = new MenuItem("Cut (Ctrl+X)");
        item.setOnAction((event) -> onCut());
        contextMenu.getItems().add(item);

        item = new MenuItem("Delete");
        item.setOnAction((event) -> onDelete());
        contextMenu.getItems().add(item);

        item = new MenuItem("Rename");
        item.setOnAction((event) -> SalmonDialogs.promptRenameFile(items.get(0).getSalmonFile()));
        contextMenu.getItems().add(item);

        item = new MenuItem("Export (Ctrl+E)");
        item.setOnAction((event) -> onExport());
        contextMenu.getItems().add(item);

        item = new MenuItem("Export And Delete (Ctrl+U)");
        item.setOnAction((event) -> onExportAndDelete());
        contextMenu.getItems().add(item);

        item = new MenuItem("Properties");
        item.setOnAction((event) -> SalmonDialogs.showProperties(items.get(0).getSalmonFile()));
        contextMenu.getItems().add(item);

        Point p = MouseInfo.getPointerInfo().getLocation();
        contextMenu.show(stage, p.x, p.y);
    }

    protected void openItem(int position) throws Exception {
        SalmonFileViewModel selectedFile = fileItemList.get(position);
        manager.openItem(selectedFile.getSalmonFile());
    }

    private SalmonFileViewModel getViewModel(SalmonFile item) {
        for (SalmonFileViewModel vm : fileItemList) {
            if (vm.getSalmonFile() == item)
                return vm;
        }
        return null;
    }

    private boolean OpenListItem(SalmonFile file) {
        SalmonFileViewModel vm = getViewModel(file);
        try {
            if (SalmonFileUtils.isVideo(file.getBaseName())) {
                startMediaPlayer(vm);
                return true;
            } else if (SalmonFileUtils.isAudio(file.getBaseName())) {
                startMediaPlayer(vm);
                return true;
            } else if (SalmonFileUtils.isImage(file.getBaseName())) {
                startImageViewer(vm);
                return true;
            } else if (SalmonFileUtils.isText(file.getBaseName())) {
                startTextEditor(vm);
                return true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            new SalmonDialog(Alert.AlertType.WARNING, "Could not open: " + ex).show();
        }
        return false;
    }

    private void startTextEditor(SalmonFileViewModel item) {
        try {
            if (item.getSalmonFile().getSize() > MAX_TEXT_FILE) {
                new SalmonDialog(Alert.AlertType.WARNING, "File too large").show();
                return;
            }
            TextEditorController.openTextEditor(item, stage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startImageViewer(SalmonFileViewModel item) {
        try {
            ImageViewerController.openImageViewer(item, stage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startMediaPlayer(SalmonFileViewModel item) {
        try {
            MediaPlayerController.openMediaPlayer(item, stage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}