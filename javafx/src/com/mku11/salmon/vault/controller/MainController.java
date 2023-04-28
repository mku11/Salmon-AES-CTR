package com.mku11.salmon.vault.controller;
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

import com.mku11.file.JavaDrive;
import com.mku11.file.JavaFile;
import com.mku11.media.SalmonMediaDataSource;
import com.mku11.salmon.SalmonGenerator;
import com.mku11.salmon.func.Consumer;
import com.mku11.salmon.streams.SalmonStream;
import com.mku11.salmon.vault.config.Config;
import com.mku11.salmon.vault.dialog.ActivityCommon;
import com.mku11.salmon.vault.dialog.SalmonAlert;
import com.mku11.salmon.vault.model.FileItem;
import com.mku11.salmon.vault.model.SalmonFileItem;
import com.mku11.salmon.vault.prefs.Preferences;
import com.mku11.salmon.vault.sequencer.WinClientSequencer;
import com.mku11.salmon.vault.settings.Settings;
import com.mku11.salmon.vault.utils.URLUtils;
import com.mku11.salmon.vault.utils.Utils;
import com.mku11.salmonfs.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.MenuItem;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainController {
    private static final int BUFFER_SIZE = 512 * 1024;
    private static final int THREADS = 4;
    public static final String SEQUENCER_DIR_PATH = System.getenv("LOCALAPPDATA") + File.separator + ".salmon";
    public static final String SEQUENCER_FILE_PATH = SEQUENCER_DIR_PATH + File.separator + "config.xml";
    private static final String SERVICE_PIPE_NAME = "SalmonService";

    public static SalmonFile RootDir;
    public static SalmonFile CurrDir;

    private FileCommander fileCommander;
    private SalmonFile[] copyFiles;

    @FXML
    public final ObservableList<FileItem> fileItemList = FXCollections.observableArrayList();

    @FXML
    public TableView<FileItem> table;
    private Stage stage;
    private String searchTerm;

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
    private final SimpleDoubleProperty filesprogress = new SimpleDoubleProperty();

    @FXML
    public SimpleDoubleProperty filesprogressProperty() {
        return filesprogress;
    }

    public Double getFilesprogress() {
        return filesprogress.get();
    }

    @FXML
    private void initialize() {
        setupTable();
        setupFileCommander();
        setupListeners();
        loadSettings();
        setupSalmonManager();
    }

    @SuppressWarnings("unchecked")
    private void setupTable() {
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setItems(fileItemList);
        table.setRowFactory(tv -> {
            TableRow<FileItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && (!row.isEmpty())) {
                    TableView.TableViewSelectionModel<FileItem> rowData = table.getSelectionModel();
                    onOpenItem(rowData.getSelectedIndex());
                } else if (event.getButton() == MouseButton.SECONDARY) {
                    ObservableList<FileItem> items = table.getSelectionModel().getSelectedItems();
                    openContextMenu(items);
                }
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                event.consume();
                TableView.TableViewSelectionModel<FileItem> rowData = table.getSelectionModel();
                onOpenItem(rowData.getSelectedIndex());
            }
        });
        TableColumn<FileItem, ImageView> columnChild = (TableColumn<FileItem, ImageView>) table.getColumns().get(0);
        columnChild.setCellFactory(new TextCellCallback());
        Platform.runLater(() -> table.requestFocus());
    }

    private void promptDelete() {
        ActivityCommon.promptDialog("Delete", "Delete " + getSelectedFiles().length + " item(s)?",
                "Ok", (buttonType) -> {
                    deleteSelectedFiles();
                    return null;
                }, "Cancel", null);
    }

    public void onAbout() {
        ActivityCommon.promptDialog("Salmon",
                Config.APP_NAME + " " + Config.VERSION + "\n" + Config.ABOUT_TEXT,
                "Get Source Code", (ButtonType) -> {
                    URLUtils.goToUrl(Config.SourceCodeURL);
                    return null;
                }, "Cancel", null);
    }

    public void onOpenVault() {
        File selectedDirectory = MainController.selectDirectory(stage, "Select directory of existing vault");
        if (selectedDirectory == null)
            return;
        String filePath = selectedDirectory.getAbsolutePath();
        try {
            ActivityCommon.openVault(filePath);
        } catch (Exception e) {
            new SalmonAlert(Alert.AlertType.WARNING, "Could not open vault: " + e).show();
        }
        refresh();
    }

    public void onCreateVault() {
        File selectedDirectory = MainController.selectDirectory(stage, "Select directory for your new vault");
        if (selectedDirectory == null)
            return;
        ActivityCommon.promptSetPassword((String pass) ->
        {
            String filePath = selectedDirectory.getAbsolutePath();
            try {
                ActivityCommon.createVault(filePath, pass);
                RootDir = SalmonDriveManager.getDrive().getVirtualRoot();
                CurrDir = RootDir;
                refresh();
            } catch (Exception e) {
                new SalmonAlert(Alert.AlertType.WARNING, "Could not create vault: " + e).show();
            }
            return null;
        });
    }

    class TextCellCallback implements Callback<TableColumn<FileItem, ImageView>, TableCell<FileItem, ImageView>> {
        public TextCellCallback() {
        }

        @Override
        public TableCell<FileItem, ImageView> call(TableColumn<FileItem, ImageView> param) {
            TableCell<FileItem, ImageView> cell = getTextCell();
            return cell;
        }
    }

    private TableCell<FileItem, ImageView> getTextCell() {
        TableCell<FileItem, ImageView> cell = new TableCell<FileItem, ImageView>() {
            @Override
            public void updateItem(ImageView newValue, boolean empty) {
                TableRow<FileItem> tableRow = getTableRow();
                if (tableRow == null)
                    return;
                FileItem item = tableRow.getItem();
                // clear item
                setGraphic(null);
                if (item != null) {
                    try {
                        setGraphic(item.getImage());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        return cell;
    }

    private void onOpenItem(int selectedItem) {
        try {
            openItem(selectedItem);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onShow() {

    }

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(event -> {
            stage.close();
            Platform.exit();
            System.exit(0);
        });

        stage.setOnShowing(event -> Platform.runLater(() -> onShow()));
//        stage.getScene().setOnKeyPressed(event -> {
//            if (event.getCode() == KeyCode.DOWN && !table.isFocused())
//            {
//                event.consume();
//                table.requestFocus();
//                table.getSelectionModel().select(0);
////                    DataGridRow row = (DataGridRow)dataGrid.ItemContainerGenerator.ContainerFromIndex(0);
////                    row.MoveFocus(new TraversalRequest(FocusNavigationDirection.Next));
//            }
//        });
    }

    public void shortcutPressed() {
        //TODO:
    }

    public void onRefresh() {
        refresh();
    }

    public void onImport() {
        promptImportFiles();
    }

    public void onExport() {
        exportSelectedFiles();
    }

    public void onNewFolder() {
        promptNewFolder();
    }

    public void onCopy() {
        if (!table.isFocused())
            return;
        mode = Mode.Copy;
        copyFiles = getSelectedFiles();
        showTaskRunning(true, false);
        showTaskMessage(copyFiles.length + " Items selected for copy");
    }

    private SalmonFile[] getSelectedFiles() {
        ObservableList<FileItem> selectedItems = table.getSelectionModel().getSelectedItems();
        SalmonFile[] files = new SalmonFile[selectedItems.size()];
        int index = 0;
        for (FileItem item : selectedItems)
            files[index++] = ((SalmonFileItem) item).getSalmonFile();
        return files;
    }

    private SalmonFile[] getSelectedFile() {
        ObservableList<FileItem> selectedItems = table.getSelectionModel().getSelectedItems();
        SalmonFile[] files = new SalmonFile[selectedItems.size()];
        int index = 0;
        for (FileItem item : selectedItems)
            files[index++] = ((SalmonFileItem) item).getSalmonFile();
        return files;
    }

    public void onCut() {
        if (!table.isFocused())
            return;

        mode = Mode.Move;
        copyFiles = getSelectedFiles();
        showTaskRunning(true, false);
        showTaskMessage(copyFiles.length + " Items selected for move");
    }

    public void onDelete() {
        if (!table.isFocused())
            return;
        promptDelete();
    }

    public void onPaste() {
        if (!table.isFocused())
            return;
        pasteSelected();
    }

    public void onSearch() {
        promptSearch();
    }

    public void onStop() {
        fileCommander.cancel();
        mode = Mode.Browse;
        ActivityCommon.runLater(() -> showTaskRunning(false), 1000);
    }

    public void onSettings() {
        try {
            SettingsController.openSettings(stage);
            loadSettings();
            MainController.setupSalmonManager();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onExit() {
        ActivityCommon.promptDialog("Exit", "Exit App",
                "ok", (buttonType) -> {
                    stage.close();
                    Platform.exit();
                    System.exit(0);
                    return null;
                }, "cancel", null);
    }

    enum MediaType {
        AUDIO, VIDEO
    }

    final Comparator<FileItem> defaultComparator = (FileItem c1, FileItem c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else {
            try {
                return c1.getBaseName().compareTo(c2.getBaseName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return 0;
    };
    private Mode mode = Mode.Browse;

    // we queue all import export jobs with an executor
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private SalmonFile[] salmonFiles;

    private void setupFileCommander() {
        fileCommander = new FileCommander(BUFFER_SIZE, THREADS);
    }

    private void loadSettings() {
        SalmonStream.setProviderType(SalmonStream.ProviderType.valueOf(Settings.getInstance().aesType.name()));
        SalmonGenerator.setPbkdfType(SalmonGenerator.PbkdfType.valueOf(Settings.getInstance().pbkdfType.name()));
        SalmonGenerator.setPbkdfAlgo(SalmonGenerator.PbkdfAlgo.valueOf(Settings.getInstance().pbkdfAlgo.name()));
        SalmonFileExporter.setEnableLog(Settings.getInstance().enableLog);
        SalmonFileExporter.setEnableLogDetails(Settings.getInstance().enableLogDetails);
        SalmonFileImporter.setEnableLog(Settings.getInstance().enableLog);
        SalmonFileImporter.setEnableLogDetails(Settings.getInstance().enableLogDetails);
        SalmonStream.setEnableLogDetails(Settings.getInstance().enableLogDetails);
        SalmonMediaDataSource.setEnableLog(Settings.getInstance().enableLogDetails);
    }

    private void setupListeners() {
        fileCommander.setImporterProgressListener((IRealFile file, long bytesRead, long totalBytesRead, String message) ->
                Platform.runLater(() -> {
                    status.setValue(message);
                    fileprogress.setValue(bytesRead / (double) totalBytesRead);
                }));

        fileCommander.setExporterProgressListener((SalmonFile file, long bytesWritten, long totalBytesWritten, String message) ->
                Platform.runLater(() -> {
                    status.setValue(message);
                    fileprogress.setValue(bytesWritten / (double) totalBytesWritten);
                }));
    }

    protected void setupRootDir() {
        String vaultLocation = Settings.getInstance().vaultLocation;
        try {
            SalmonDriveManager.openDrive(vaultLocation);
            SalmonDriveManager.getDrive().setEnableIntegrityCheck(true);
            RootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            CurrDir = RootDir;
            if (RootDir == null) {
                promptSelectRoot();
                return;
            }
        } catch (SalmonAuthException e) {
            checkCredentials();
            return;
        } catch (Exception e) {
            e.printStackTrace();
            promptSelectRoot();
            return;
        }
        refresh();
    }

    public void refresh() {
        if (checkFileSearcher())
            return;
        if (SalmonDriveManager.getDrive() == null)
            return;
        try {
            if (SalmonDriveManager.getDrive().getVirtualRoot() == null || !SalmonDriveManager.getDrive().getVirtualRoot().exists()) {
                promptSelectRoot();
                return;
            }
            if (!SalmonDriveManager.getDrive().isAuthenticated()) {
                checkCredentials();
                return;
            }
            executor.submit(() -> {
                if (mode != Mode.Search)
                    salmonFiles = CurrDir.listFiles();
                Platform.runLater(() -> {
                    SalmonFile selectedFile = null;
                    if (table.getSelectionModel().getSelectedItem() != null)
                        selectedFile = ((SalmonFileItem) table.getSelectionModel().getSelectedItem()).getSalmonFile();
                    displayFiles(selectedFile);
                });
            });
        } catch (SalmonAuthException e) {
            checkCredentials();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkFileSearcher() {
        if (fileCommander.isFileSearcherRunning()) {
            new SalmonAlert(Alert.AlertType.WARNING, "Another process is running").show();
            return true;
        }
        return false;
    }

    private void displayFiles(SalmonFile selectedFile) {
        Platform.runLater(() -> {
            try {
                if (mode == Mode.Search)
                    setPath(CurrDir.getPath() + "?search=" + searchTerm);
                else
                    setPath(CurrDir.getPath());
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            fileItemList.clear();
            for (SalmonFile file : salmonFiles) {
                try {
                    SalmonFileItem item = new SalmonFileItem(file);
                    fileItemList.add(item);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (mode != Mode.Search)
                sortFiles();
            try {
                selectItem(selectedFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void selectItem(SalmonFile selectedFile) throws Exception {
        int index = 0;
        for (FileItem file : fileItemList) {
            if (selectedFile != null && ((SalmonFileItem) file).getSalmonFile().getPath().equals(selectedFile.getPath())) {
                table.getSelectionModel().select(index);
                table.scrollTo(table.selectionModelProperty().get().getSelectedIndex());
                Platform.runLater(() -> {
                    table.requestFocus();
                });
                break;
            }
            index++;
        }
    }

    public static void setupSalmonManager() {
        try {
            SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
            if(SalmonDriveManager.getSequencer() != null)
                SalmonDriveManager.getSequencer().close();
            if(Settings.getInstance().authType == Settings.AuthType.User) {
                setupFileSequencer();
            } else if(Settings.getInstance().authType == Settings.AuthType.Service) {
                setupClientSequencer();
            }
        } catch (Exception e) {
            e.printStackTrace();
            new SalmonAlert(Alert.AlertType.ERROR, "Error during initializing: " + e.getMessage()).show();
        }
    }

    private static void setupFileSequencer() throws Exception {
        IRealFile dirFile = new JavaFile(SEQUENCER_DIR_PATH);
        if (!dirFile.exists())
            dirFile.mkdir();
        IRealFile seqFile = new JavaFile(SEQUENCER_FILE_PATH);
        FileSequencer sequencer = new FileSequencer(seqFile, new SalmonSequenceParser());
        SalmonDriveManager.setSequencer(sequencer);
    }

    private static void setupClientSequencer() throws Exception {
        try {
            SalmonDriveManager.setSequencer(new WinClientSequencer(SERVICE_PIPE_NAME));
        } catch (Exception ex) {
            ex.printStackTrace();
            new SalmonAlert(Alert.AlertType.ERROR, "Error during service lookup. Make sure the Salmon Service is installed and running:\n" + ex.getMessage()).show();
        }

    }

    private void pasteSelected() {
        copySelectedFiles(mode == Mode.Move);
    }

    private void promptSearch() {
        ActivityCommon.promptEdit("Search", "Keywords", "", "Match any term", false, this::search, false);
    }

    public void showTaskRunning(boolean value) {
        showTaskRunning(value, true);
    }

    public void showTaskRunning(boolean value, boolean progress) {
        Platform.runLater(() -> {
            fileprogress.setValue(0);
            filesprogress.setValue(0);
            if (progress)
                progressVisibility.setValue(value);
            if (!value)
                status.setValue("");
        });
    }

    public void showTaskMessage(String msg) {
        Platform.runLater(() -> status.setValue(msg == null ? "" : msg));
    }

    private void sortFiles() {
        fileItemList.sort(defaultComparator);
    }

    private void promptImportFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Files");
        if (Settings.getInstance().lastImportDir != null) {
            File lastDir = new File(Settings.getInstance().lastImportDir);
            if (lastDir.exists() && lastDir.isDirectory())
                fileChooser.setInitialDirectory(lastDir);
        }
        List<File> files = fileChooser.showOpenMultipleDialog(stage);
        if (files == null)
            return;
        Settings.getInstance().lastImportDir = files.get(0).getParent();
        Preferences.savePrefs();
        JavaFile[] filesToImport = new JavaFile[files.size()];
        int count = 0;
        for (File file : files)
            filesToImport[count++] = new JavaFile(file.getAbsolutePath());
        importFiles(filesToImport, CurrDir, Settings.getInstance().deleteAfterImport, (SalmonFile[] importedFiles) ->
        {
            Platform.runLater(this::refresh);
        });
    }

    private void promptNewFolder() {
        ActivityCommon.promptEdit("New Folder", "Folder Name",
                "New Folder", null, false, (String folderName, Boolean checked) -> {
                    try {
                        CurrDir.createDirectory(folderName, null, null);
                        refresh();
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        new SalmonAlert(Alert.AlertType.ERROR, "Could Not Create Folder: " + exception.getMessage()).show();
                        refresh();
                    }
                }
                , false);
    }

    private void deleteSelectedFiles() {
        deleteFiles(getSelectedFiles());
    }

    private void copySelectedFiles(boolean move) {
        copyFiles(copyFiles, CurrDir, move);
    }

    private void deleteFiles(SalmonFile[] files) {
        if (files.length == 0) {
            status.setValue("Select 1 or more files");
            return;
        }
        executor.submit(() ->
        {
            showTaskRunning(true);
            try {
                fileCommander.deleteFiles(files, (file) -> {
                    Platform.runLater(() -> {
                        fileItemList.remove(file);
                        sortFiles();
                    });
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            refresh();
            Platform.runLater(() -> {
                fileprogress.setValue(100);
                filesprogress.setValue(100);
            });
            ActivityCommon.runLater(() -> showTaskRunning(false), 1000);
        });
    }

    private void copyFiles(SalmonFile[] files, SalmonFile dir, boolean move) {
        if (files.length == 0) {
            status.setValue("Select 1 or more files");
            return;
        }
        executor.submit(() ->
        {
            showTaskRunning(true);
            try {
                fileCommander.copyFiles(files, dir, move, (fileInfo) -> {
                    Platform.runLater(() -> {
                        fileprogress.setValue(fileInfo.fileProgress);
                        filesprogress.setValue(fileInfo.processedFiles / (double) fileInfo.totalFiles);
                        String action = move ? " Moving: " : " Copying: ";
                        showTaskMessage((fileInfo.processedFiles + 1) + "/" + fileInfo.totalFiles + action + fileInfo.filename);
                    });
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            Platform.runLater(() -> {
                fileprogress.setValue(100);
                filesprogress.setValue(100);
                refresh();
            });
            ActivityCommon.runLater(() -> showTaskRunning(false), 1000);
            copyFiles = null;
            mode = Mode.Browse;
        });
    }

    private void exportSelectedFiles() {
        if (RootDir == null || !SalmonDriveManager.getDrive().isAuthenticated())
            return;
        SalmonFile[] files = getSelectedFiles();
        if (files.length == 0) {
            status.setValue("Select 1 or more files");
            return;
        }
        exportFiles(files, (exportedFiles) ->
        {
            refresh();
        });
    }

    private void openContextMenu(ObservableList<FileItem> items) {
        ContextMenu contextMenu = new ContextMenu();

		MenuItem item = new MenuItem("View");
        item.setOnAction((event) -> onOpenItem(table.getSelectionModel().getSelectedIndex()));
        contextMenu.getItems().add(item);
		
		item = new MenuItem("View as Text");
        item.setOnAction((event) -> startTextEditor(table.getSelectionModel().getSelectedIndex()));
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
        item.setOnAction((event) -> renameFile(items.get(0)));
        contextMenu.getItems().add(item);

		item = new MenuItem("Export (Ctrl+E)");
        item.setOnAction((event) -> onExport());
        contextMenu.getItems().add(item);
		
        item = new MenuItem("Properties");
        item.setOnAction((event) -> showProperties(((SalmonFileItem) items.get(0)).getSalmonFile()));
        contextMenu.getItems().add(item);

        Point p = MouseInfo.getPointerInfo().getLocation();
        contextMenu.show(stage, p.x, p.y);
    }

    private void renameFile(FileItem ifile) {
        Platform.runLater(() -> {
            try {
                ActivityCommon.promptEdit("Rename", "New filename",
                        ifile.getBaseName(), null, true, (String newFilename, Boolean checked) -> {
                            try {
                                ifile.rename(newFilename);
                            } catch (Exception exception) {
                                exception.printStackTrace();
                            }
                        }, false);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    private void showProperties(SalmonFile ifile) {
        try {
            ActivityCommon.promptDialog("Properties",
                    "Name: " + ifile.getBaseName() + "\n" +
                            "Path: " + ifile.getPath() + "\n" +
                            "Size: " + Utils.getBytes(ifile.getSize(), 2) + " (" + ifile.getSize() + " bytes)" + "\n" +
                            "\n" +
                            "EncryptedName: " + ifile.getRealFile().getBaseName() + "\n" +
                            "EncryptedPath: " + ifile.getRealFile().getAbsolutePath() + "\n" +
                            "EncryptedSize: " + Utils.getBytes(ifile.getRealFile().length(), 2) + " (" + ifile.getRealFile().length() + " bytes)" + "\n"
                    , "ok", null,
                    null, null
            );
        } catch (Exception exception) {
            status.setValue("Could not get file properties");
            exception.printStackTrace();
        }
    }

    public void onCloseVault() {
        logout();
        RootDir = null;
        CurrDir = null;
        Platform.runLater(fileItemList::clear);
    }

    private void checkCredentials() {
        if (SalmonDriveManager.getDrive().hasConfig()) {
            ActivityCommon.promptPassword((pass) ->
            {
                try {
                    RootDir = SalmonDriveManager.getDrive().getVirtualRoot();
                    CurrDir = RootDir;
                } catch (SalmonAuthException e) {
                    e.printStackTrace();
                }
                refresh();
                return null;
            });
        }
    }

    protected void openItem(int position) throws Exception {
        FileItem selectedFile = fileItemList.get(position);
        if (selectedFile.isDirectory()) {
            executor.submit(() -> {
                if (checkFileSearcher())
                    return;
                CurrDir = ((SalmonFileItem) selectedFile).getSalmonFile();
                salmonFiles = CurrDir.listFiles();
                displayFiles(null);
            });
            return;
        }
        String filename = selectedFile.getBaseName();
		try {
        if (FileUtils.isVideo(filename)) {
            startMediaPlayer(position, MediaType.VIDEO);
        } else if (FileUtils.isAudio(filename)) {
            startMediaPlayer(position, MediaType.AUDIO);
        } else if (FileUtils.isImage(filename)) {
            startImageViewer(position);
        } else if (FileUtils.isText(filename)) {
            startTextEditor(position);
        }
		} catch (Exception ex) {
			ex.printStackTrace();
			new SalmonAlert(Alert.AlertType.WARNING, "Could not open: " + ex).show();
		}
    }

    private void startTextEditor(int position) {
        FileItem item = fileItemList.get(position);
		try {
            if (salmonFiles[position].getSize() > 1 * 1024 * 1024) {
                new SalmonAlert(Alert.AlertType.WARNING, "File too large").show();
				return;
            }
			TextEditorController.openTextEditor(item, stage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void startImageViewer(int position) {
        FileItem item = fileItemList.get(position);
        try {
            ImageViewerController.openImageViewer(item, stage);
        } catch (IOException e) {
            e.printStackTrace();
        }
        refresh();
    }

    private void startMediaPlayer(int position, MediaType type) {
        FileItem item = fileItemList.get(position);
        try {
            MediaPlayerController.openMediaPlayer(item, stage, type);
        } catch (IOException e) {
            e.printStackTrace();
        }
        refresh();
    }

    private void logout() {
        try {
            SalmonDriveManager.closeDrive();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void promptSelectRoot() {
        ActivityCommon.promptDialog("Vault", "Choose a location for your vault", "ok",
                (buttonType) -> {
                    File selectedDirectory = selectDirectory(stage, "Select vault directory");
                    if (selectedDirectory == null)
                        return null;
                    String filePath = selectedDirectory.getAbsolutePath();
                    onCloseVault();
                    try {
                        ActivityCommon.openVault(filePath);
                        setupRootDir();
                    } catch (Exception e) {
                        new SalmonAlert(Alert.AlertType.WARNING, "Could not open vault: " + e).show();
                    }
                    return null;
                }, "cancel", null);
    }

    public static File selectDirectory(Stage stage, String title) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);
        if(Settings.getInstance().vaultLocation != null) {
            File initDir = new File(Settings.getInstance().vaultLocation);
            if(initDir.exists() && initDir.isDirectory())
                directoryChooser.setInitialDirectory(new File(Settings.getInstance().vaultLocation));
        }
        File selectedDirectory = directoryChooser.showDialog(stage);
        return selectedDirectory;
    }

    public void onBack() {
        SalmonFile parent = CurrDir.getParent();
        if (mode == Mode.Search && fileCommander.isFileSearcherRunning()) {
            fileCommander.stopFileSearch();
        } else if (mode == Mode.Search) {
            executor.submit(() -> {
                mode = Mode.Browse;
                salmonFiles = CurrDir.listFiles();
                displayFiles(null);
            });
        } else if (parent != null) {
            executor.submit(() -> {
                if (checkFileSearcher())
                    return;
                SalmonFile parentDir = CurrDir;
                CurrDir = parent;
                salmonFiles = CurrDir.listFiles();
                displayFiles(parentDir);
            });
        }
    }

    enum Mode {
        Browse, Search, Copy, Move
    }

    public void exportFiles(SalmonFile[] items, final Consumer<IRealFile[]> OnFinished) {
        executor.submit(() ->
        {
            for(SalmonFile file : items){
                if(file.isDirectory()) {
                    Platform.runLater(() -> new SalmonAlert(Alert.AlertType.ERROR, "Cannot Export Directories select files only").show());
                    return;
                }
            }

            showTaskRunning(true);
            boolean success = false;
            try {
                success = fileCommander.exportFiles(items,
                        (progress,file) -> {
                            Platform.runLater(() -> filesprogress.setValue(progress / 100F));
                        }, OnFinished);
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> new SalmonAlert(Alert.AlertType.ERROR, "Error while exporting files: " + e).show());
            }
            if (fileCommander.isStopped())
                showTaskMessage("ExportStopped");
            else if (!success)
                showTaskMessage("ExportFailed");
            else
                showTaskMessage("ExportComplete");
            Platform.runLater(() -> {
                fileprogress.setValue(100);
                filesprogress.setValue(100);
                ActivityCommon.promptDialog("Export", "Files Exported To: " + SalmonDriveManager.getDrive().getExportDir().getAbsolutePath(),
                        "ok", null, null, null);
            });
            ActivityCommon.runLater(() ->
                    showTaskRunning(false), 1000);

        });
    }

    public void importFiles(IRealFile[] fileNames, SalmonFile importDir, boolean deleteSource,
                            Consumer<SalmonFile[]> OnFinished) {

        executor.submit(() ->
        {
            showTaskRunning(true);
            boolean success = false;
            try {
                success = fileCommander.importFiles(fileNames, importDir, deleteSource,
                        (progress, file) -> {
                            Platform.runLater(() -> filesprogress.setValue(progress / 100F));
                        }, OnFinished);
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> new SalmonAlert(Alert.AlertType.ERROR, "Error while importing files: " + e).show());
            }
            if (fileCommander.isStopped())
                showTaskMessage("Import Stopped");
            else if (!success)
                showTaskMessage("Import Failed");
            else showTaskMessage("Import Complete");
            Platform.runLater(() -> {
                fileprogress.setValue(100);
                filesprogress.setValue(100);
            });
            ActivityCommon.runLater(() ->
                    showTaskRunning(false), 1000);
        });
    }


    //TODO: refactor to a class and update ui frequently with progress
    private void search(String value, boolean any) {
        searchTerm = value;
        if (checkFileSearcher())
            return;
        executor.submit(() -> {
            mode = Mode.Search;
            try {
                setPath(CurrDir.getPath() + "?search=" + value);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            salmonFiles = new SalmonFile[]{};
            fileItemList.clear();
            salmonFiles = fileCommander.search(CurrDir, value, any, (SalmonFile salmonFile) -> Platform.runLater(() -> {
                int position = 0;
                for (FileItem file : fileItemList) {
                    if ((int) salmonFile.getTag() > (int) ((SalmonFileItem) file).getSalmonFile().getTag()) {
                        break;
                    } else
                        position++;
                }
                SalmonFileItem item;
                try {
                    item = new SalmonFileItem(salmonFile);
                    fileItemList.add(position, item);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
            Platform.runLater(() -> {
                if (!fileCommander.isFileSearcherStopped())
                    status.setValue("Search: " + value);
                else
                    status.setValue("Search Stopped: " + value);
            });
        });
    }

    public void onImportAuth() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Auth File");
        String filename = SalmonDriveManager.getAppDriveConfigFilename();
        String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(filename);
        FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("Salmon Auth Files (*." + ext + ")", "*." + ext);
        fileChooser.getExtensionFilters().add(filter);
        File file = fileChooser.showOpenDialog(stage);
        if (file == null)
            return;
        try {
            SalmonDriveManager.importAuthFile(file.getPath());
            new SalmonAlert(Alert.AlertType.ERROR, "Device is now Authorized").show();
        } catch (Exception ex) {
            ex.printStackTrace();
            new SalmonAlert(Alert.AlertType.ERROR, "Could Not Import Auth: " + ex.getMessage()).show();
        }
    }

    public void onExportAuth() {
        if (SalmonDriveManager.getDrive() == null) {
            new SalmonAlert(Alert.AlertType.ERROR, "No Drive Loaded").show();
            return;
        }
        ActivityCommon.promptEdit("Export Auth File",
                "Enter the Auth ID for the device you want to authorize",
                "", null, false,
                (targetAuthID, option) -> {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Export Auth File");
                    String filename = SalmonDriveManager.getAppDriveConfigFilename();
                    String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(filename);
                    fileChooser.setInitialFileName(filename);
                    FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("Salmon Auth Files (*." + ext + ")", "*." + ext);
                    fileChooser.getExtensionFilters().add(filter);
                    File file = fileChooser.showSaveDialog(stage);
                    if (file == null)
                        return;
                    try {
                        SalmonDriveManager.exportAuthFile(targetAuthID, file.getParent(), file.getName());
                        new SalmonAlert(Alert.AlertType.INFORMATION, "Auth File Exported").show();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        new SalmonAlert(Alert.AlertType.ERROR, "Could Not Export Auth: " + ex).show();
                    }
                }, false);
    }


    public void onRevokeAuth() {
        if (SalmonDriveManager.getDrive() == null) {
            new SalmonAlert(Alert.AlertType.ERROR, "No Drive Loaded").show();
            return;
        }
        ActivityCommon.promptDialog("Revoke Auth", "Revoke Auth for this drive? You will still be able to decrypt and view your files but you won't be able to import any more files in this drive.",
                "Ok", (buttonType) -> {
                    try {
                        SalmonDriveManager.revokeSequences();
                        new SalmonAlert(Alert.AlertType.ERROR, "Revoke Auth Successful").show();
                    } catch (Exception e) {
                        e.printStackTrace();
                        new SalmonAlert(Alert.AlertType.ERROR, "Could Not Revoke Auth: " + e.getMessage()).show();
                    }
                    return null;
                }, "Cancel", null);
    }


    public void onDisplayAuthID() throws Exception {
        if (SalmonDriveManager.getDrive() == null) {
            new SalmonAlert(Alert.AlertType.ERROR, "No Drive Loaded").show();
            return;
        }
        String driveID = SalmonDriveManager.getAuthID();
        ActivityCommon.promptEdit("Salmon Auth ID",
                "", driveID, null, false,
                null, true);
    }

}
