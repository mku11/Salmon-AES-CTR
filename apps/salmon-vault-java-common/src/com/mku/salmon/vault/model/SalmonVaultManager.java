package com.mku.salmon.vault.model;
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

import com.mku.file.IRealFile;
import com.mku.file.JavaFile;
import com.mku.func.BiConsumer;
import com.mku.func.Consumer;
import com.mku.func.Function;
import com.mku.salmon.SalmonRangeExceededException;
import com.mku.salmon.SalmonSecurityException;
import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.salmon.io.SalmonStream;
import com.mku.salmon.password.SalmonPassword;
import com.mku.salmon.vault.config.SalmonConfig;
import com.mku.salmon.vault.dialog.SalmonDialog;
import com.mku.salmon.vault.dialog.SalmonDialogs;
import com.mku.salmon.vault.prefs.SalmonPreferences;
import com.mku.salmon.vault.utils.ByteUtils;
import com.mku.salmon.vault.utils.IPropertyNotifier;
import com.mku.salmonfs.SalmonAuthException;
import com.mku.salmonfs.SalmonDriveManager;
import com.mku.salmonfs.SalmonFile;
import com.mku.sequence.ISalmonSequenceSerializer;
import com.mku.sequence.SalmonFileSequencer;
import com.mku.sequence.SalmonSequenceException;
import com.mku.sequence.SalmonSequenceSerializer;
import com.mku.utils.SalmonFileCommander;
import com.mku.utils.SalmonFileExporter;
import com.mku.utils.SalmonFileImporter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SalmonVaultManager implements IPropertyNotifier {
    protected static final String SEQUENCER_DIR_NAME = ".salmon";
    protected static final String SERVICE_PIPE_NAME = "SalmonService";

    private static final int BUFFER_SIZE = 512 * 1024;
    private static final int THREADS = 3;

    public static final int REQUEST_OPEN_VAULT_DIR = 1000;
    public static final int REQUEST_CREATE_VAULT_DIR = 1001;
    public static final int REQUEST_IMPORT_FILES = 1002;
    public static final int REQUEST_EXPORT_DIR = 1003;
    public static final int REQUEST_IMPORT_AUTH_FILE = 1004;
    public static final int REQUEST_EXPORT_AUTH_FILE = 1005;

    private static ExecutorService executor = Executors.newFixedThreadPool(4);

    private String sequencerDefaultDirPath = SalmonConfig.getPrivateDir() + File.separator + SEQUENCER_DIR_NAME;
    private HashSet<BiConsumer<Object, String>> observers = new HashSet<>();

    public String getSequencerDefaultDirPath() {
        return sequencerDefaultDirPath;
    }

    public void setSequencerDefaultDirPath(String value) {
        sequencerDefaultDirPath = value;
    }

    protected String getSequencerFilepath() {
        return sequencerDefaultDirPath + File.separator
                + SalmonConfig.FILE_SEQ_FILENAME;
    }

    public Function<SalmonFile, Boolean> openListItem;
    public Consumer<SalmonFile> updateListItem;
    public BiConsumer<Integer, SalmonFile> onFileItemAdded;

    protected static SalmonVaultManager instance;

    synchronized
    public static SalmonVaultManager getInstance() {
        if (instance == null) {
            instance = new SalmonVaultManager();
        }
        return instance;
    }

    private List<SalmonFile> fileItemList;

    public List<SalmonFile> getFileItemList() {
        return fileItemList;
    }

    public void setFileItemList(List<SalmonFile> value) {
        if (fileItemList != value) {
            fileItemList = value;
            propertyChanged(this, "FileItemList");
        }
    }

    private HashSet<SalmonFile> selectedFiles = new HashSet<>();

    public HashSet<SalmonFile> getSelectedFiles() {
        return selectedFiles;
    }

    public void setSelectedFiles(HashSet<SalmonFile> value) {
        if (value != selectedFiles) {
            selectedFiles = value;
            propertyChanged(this, "SelectedFiles");
        }
    }

    private SalmonFile _currentItem;

    public SalmonFile getCurrentItem() {
        return _currentItem;
    }

    public void setCurrentItem(SalmonFile value) {
        if (value != _currentItem) {
            _currentItem = value;
            propertyChanged(this, "CurrentItem");
        }
    }

    private String status = "";

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        if (value != status) {
            status = value;
            propertyChanged(this, "Status");
        }
    }

    private boolean isJobRunning;

    public boolean isJobRunning() {
        return isJobRunning;
    }

    public void setJobRunning(boolean value) {
        if (value != isJobRunning) {
            isJobRunning = value;
            propertyChanged(this, "IsJobRunning");
        }
    }

    private String path;

    public String getPath() {
        return path;
    }

    public void setPath(String value) {
        if (value != path) {
            path = value;
            propertyChanged(this, "Path");
        }
    }

    private double fileProgress;

    public double getFileProgress() {
        return fileProgress;
    }

    public void setFileProgress(double value) {
        if (value != fileProgress) {
            fileProgress = value;
            propertyChanged(this, "FileProgress");
        }
    }

    private double filesProgress;

    public double getFilesProgress() {
        return filesProgress;
    }

    public void setFilesProgress(double value) {
        if (value != filesProgress) {
            filesProgress = value;
            propertyChanged(this, "FilesProgress");
        }
    }

    private SalmonFile currDir;

    public SalmonFile getCurrDir() {
        return currDir;
    }

    private SalmonFileCommander fileCommander;
    private SalmonFile[] copyFiles;
    private SalmonFile[] salmonFiles;
    private String searchTerm;
    private Mode fileManagerMode = Mode.Browse;

    public Mode getFileManagerMode() {
        return fileManagerMode;
    }

    protected SalmonVaultManager() {
        setupFileCommander();
        loadSettings();
        setupSalmonManager();
    }

    public void initialize() {
        setupDrive();
    }

    private void setupDrive() {
        if (SalmonSettings.getInstance().getVaultLocation() != null) {
            setupRootDir();
        }
    }

    public boolean onOpenItem(int selectedItem) {
        try {
            SalmonFile selectedFile = fileItemList.get(selectedItem);
            return openItem(selectedFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setPathText(String value) {
        if (value.startsWith("/"))
            value = value.substring(1);
        setPath("fs://" + value);
    }

    public void stopOperation() {
        fileCommander.cancel();
        fileManagerMode = Mode.Browse;
        setTaskRunning(false);
    }

    public void copySelectedFiles() {
        fileManagerMode = Mode.Copy;
        copyFiles = selectedFiles.toArray(new SalmonFile[0]);
        setTaskRunning(true, false);
        setTaskMessage(copyFiles.length + " Items selected for copy");
    }

    public void cutSelectedFiles() {
        fileManagerMode = Mode.Move;
        copyFiles = selectedFiles.toArray(new SalmonFile[0]);
        setTaskRunning(true, false);
        setTaskMessage(copyFiles.length + " Items selected for move");
    }

    private void setupFileCommander() {
        fileCommander = new SalmonFileCommander(BUFFER_SIZE, BUFFER_SIZE, THREADS);
    }

    private void loadSettings() {
        try {
            SalmonPreferences.loadPrefs();
            SalmonStream.setAesProviderType(SalmonStream.ProviderType.valueOf(SalmonSettings.getInstance().getAesType().toString()));
            SalmonPassword.setPbkdfType(SalmonPassword.PbkdfType.valueOf(SalmonSettings.getInstance().getPbkdfImpl().toString()));
            SalmonPassword.setPbkdfAlgo(SalmonPassword.PbkdfAlgo.valueOf(SalmonSettings.getInstance().getPbkdfAlgo().toString()));
            SalmonFileExporter.setEnableLog(SalmonSettings.getInstance().isEnableLog());
            SalmonFileExporter.setEnableLogDetails(SalmonSettings.getInstance().isEnableLogDetails());
            SalmonFileImporter.setEnableLog(SalmonSettings.getInstance().isEnableLog());
            SalmonFileImporter.setEnableLogDetails(SalmonSettings.getInstance().isEnableLogDetails());
        } catch (SalmonSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    protected void setupRootDir() {
        String vaultLocation = SalmonSettings.getInstance().getVaultLocation();
        try {
            SalmonDriveManager.openDrive(vaultLocation);
            currDir = SalmonDriveManager.getDrive().getVirtualRoot();
            if (SalmonDriveManager.getDrive().getVirtualRoot() == null) {
                SalmonDialogs.promptSelectRoot();
                return;
            }
        } catch (SalmonAuthException ex) {
            checkCredentials();
            return;
        } catch (Exception e) {
            e.printStackTrace();
            SalmonDialogs.promptSelectRoot();
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
                SalmonDialogs.promptSelectRoot();
                return;
            }
            if (!SalmonDriveManager.getDrive().isAuthenticated()) {
                checkCredentials();
                return;
            }
            executor.execute(() ->
            {
                if (fileManagerMode != Mode.Search)
                    salmonFiles = currDir.listFiles();
                SalmonFile selectedFile = selectedFiles.size() > 1 ? selectedFiles.iterator().next() : null;
                populateFileList(selectedFile);
            });
        } catch (SalmonAuthException e) {
            e.printStackTrace();
            checkCredentials();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkFileSearcher() {
        if (fileCommander.isFileSearcherRunning()) {
            SalmonDialogs.promptAnotherProcessRunning();
            return true;
        }
        return false;
    }

    private void populateFileList(SalmonFile currentFile) {
        executor.execute(() ->
        {
            selectedFiles.clear();
            try {
                if (fileManagerMode == Mode.Search)
                    setPathText(currDir.getPath() + "?search=" + searchTerm);
                else
                    setPathText(currDir.getPath());
            } catch (Exception exception) {
                exception.printStackTrace();
                SalmonDialog.promptDialog("Error", exception.getMessage());
            }

            List<SalmonFile> list = new ArrayList<>();
            for (SalmonFile file : salmonFiles) {
                try {
                    list.add(file);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            setFileItemList(list);
            SalmonFile currFile = findCurrentItem(currentFile);
            setCurrentItem(currFile);
        });
    }

    public void setupSalmonManager() {
        try {
            if (SalmonDriveManager.getSequencer() != null)
                SalmonDriveManager.getSequencer().close();
            // file sequencer for mobile is secure since it runs in sandbox
            setupFileSequencer();
        } catch (Exception e) {
            e.printStackTrace();
            SalmonDialog.promptDialog("Error", "Error during initializing: " + e.getMessage());
        }
    }

    private void setupFileSequencer() throws SalmonSequenceException, IOException {
        IRealFile dirFile = new JavaFile(getSequencerDefaultDirPath());
        if (!dirFile.exists())
            dirFile.mkdir();
        IRealFile seqFile = new JavaFile(getSequencerFilepath());
        SalmonFileSequencer sequencer = new SalmonFileSequencer(seqFile, createSerializer());
        SalmonDriveManager.setSequencer(sequencer);
    }

    protected ISalmonSequenceSerializer createSerializer() {
        return new SalmonSequenceSerializer();
    }

    public void pasteSelected() {
        copySelectedFiles(fileManagerMode == Mode.Move);
    }

    public void setTaskRunning(boolean value) {
        setTaskRunning(value, true);
    }

    public void setTaskRunning(boolean value, boolean progress) {
        if (progress)
            setJobRunning(value);
    }

    public void setTaskMessage(String msg) {
        setStatus(msg != null ? msg : "");
    }

    public void openVault(String path) {
        if (path == null)
            return;

        try {
            closeVault();
            SalmonDriveManager.openDrive(path);
            SalmonSettings.getInstance().setVaultLocation(path);
            SalmonPreferences.savePrefs();
        } catch (Exception e) {
            SalmonDialog.promptDialog("Error", "Could not open vault: " + e.getMessage());
        }
        refresh();
    }

    public void deleteSelectedFiles() {
        deleteFiles(selectedFiles.toArray(new SalmonFile[0]));
        clearSelectedFiles();
    }

    private void copySelectedFiles(boolean move) {
        copyFiles(copyFiles, currDir, move);
        clearSelectedFiles();
    }

    private void deleteFiles(SalmonFile[] files) {
        if (files == null)
            return;
        executor.execute(() ->
        {
            setFileProgress(0);
            setFilesProgress(0);

            setTaskRunning(true);

            Exception[] exception = new Exception[]{null};
            int[] processedFiles = new int[]{-1};
            List<SalmonFile> failedFiles = new ArrayList<>();
            try {
                fileCommander.deleteFiles(files,
                        (taskProgress) ->
                        {
                            if (processedFiles[0] < taskProgress.getProcessedFiles()) {
                                try {
                                    if (taskProgress.getProcessedBytes() != taskProgress.getTotalBytes()) {
                                        setTaskMessage("Deleting: " + taskProgress.getFile().getBaseName()
                                                + " " + (taskProgress.getProcessedFiles() + 1) + "/" + taskProgress.getTotalFiles());
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                processedFiles[0] = taskProgress.getProcessedFiles();
                            }
                            setFileProgress(taskProgress.getProcessedBytes() / (double) taskProgress.getTotalBytes());
                            setFilesProgress(taskProgress.getProcessedFiles() / (double) taskProgress.getTotalFiles());
                        }, (file, ex) ->
                        {
                            failedFiles.add(file);
                            exception[0] = ex;
                        });
            } catch (Exception e) {
                if (!fileCommander.areJobsStopped()) {
                    e.printStackTrace();
                    SalmonDialog.promptDialog("Error", "Could not delete files: " + e.getMessage(), "Ok");
                }
            }
            if (fileCommander.areJobsStopped())
                setTaskMessage("Delete Stopped");
            else if (failedFiles.size() > 0)
                SalmonDialog.promptDialog("Delete", "Some files failed: " + exception[0].getMessage());
            else
                setTaskMessage("Delete Complete");
            setFileProgress(1);
            setFilesProgress(1);
            refresh();
            setTaskRunning(false);
            copyFiles = null;
            fileManagerMode = Mode.Browse;
        });
    }

    private void copyFiles(SalmonFile[] files, SalmonFile dir, boolean move) {
        if (files == null)
            return;
        executor.execute(() ->
        {
            setFileProgress(0);
            setFilesProgress(0);

            setTaskRunning(true);
            String action = move ? "Moving" : "Copying";
            Exception[] exception = new Exception[]{null};
            int[] processedFiles = new int[]{-1};
            List<SalmonFile> failedFiles = new ArrayList<>();
            try {
                fileCommander.copyFiles(files, dir, move,
                        (taskProgress) ->
                        {
                            if (processedFiles[0] < taskProgress.getProcessedFiles()) {
                                try {
                                    setTaskMessage(action + ": " + taskProgress.getFile().getBaseName()
                                            + " " + (taskProgress.getProcessedFiles() + 1) + "/" + taskProgress.getTotalFiles());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                processedFiles[0] = taskProgress.getProcessedFiles();
                            }
                            setFileProgress(taskProgress.getProcessedBytes() / (double) taskProgress.getTotalBytes());
                            setFilesProgress(taskProgress.getProcessedFiles() / (double) taskProgress.getTotalFiles());
                        }, SalmonFile.autoRename, true, (file, ex) ->
                        {
                            handleThrowException(ex);
                            failedFiles.add(file);
                            exception[0] = ex;
                        });
            } catch (Exception e) {
                if (!fileCommander.areJobsStopped()) {
                    e.printStackTrace();
                    SalmonDialog.promptDialog("Error", "Could not copy files: " + e.getMessage(), "Ok");
                }
            }
            if (fileCommander.areJobsStopped())
                setTaskMessage(action + " Stopped");
            else if (failedFiles.size() > 0)
                SalmonDialog.promptDialog(action, "Some files failed: " + exception[0].getMessage());
            else
                setTaskMessage(action + " Complete");
            setFileProgress(1);
            setFilesProgress(1);
            setTaskRunning(false);
            refresh();
            copyFiles = null;
            fileManagerMode = Mode.Browse;
        });
    }

    public void exportSelectedFiles(boolean deleteSource) throws SalmonAuthException {
        if (SalmonDriveManager.getDrive().getVirtualRoot() == null || !SalmonDriveManager.getDrive().isAuthenticated())
            return;
        exportFiles(selectedFiles.toArray(new SalmonFile[0]), (files) ->
        {
            refresh();
        }, deleteSource);
        clearSelectedFiles();
    }

    private void clearSelectedFiles() {
        setSelectedFiles(new HashSet<>());
    }

    public boolean handleException(Exception exception) {
        return false;
    }

    public void closeVault() {
        try {
            fileItemList = null;
            currDir = null;
            clearCopiedFiles();
            setPathText("");
            if (SalmonDriveManager.getDrive() != null)
                SalmonDriveManager.closeDrive();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void checkCredentials() {
        if (SalmonDriveManager.getDrive().hasConfig()) {
            SalmonDialogs.promptPassword(() -> onPasswordSubmitted());
        }
    }

    public void onPasswordSubmitted() {
        try {
            currDir = SalmonDriveManager.getDrive().getVirtualRoot();
        } catch (SalmonAuthException e) {
            e.printStackTrace();
        }
        refresh();
    }

    public boolean openItem(SalmonFile selectedFile) throws SalmonSecurityException, SalmonIntegrityException, IOException, SalmonAuthException {
        int position = fileItemList.indexOf(selectedFile);
        if (position < 0)
            return true;
        if (selectedFile.isDirectory()) {
            executor.execute(() ->
            {
                if (checkFileSearcher())
                    return;
                currDir = (selectedFile);
                salmonFiles = currDir.listFiles();
                populateFileList(null);
            });
            return true;
        }
        String filename = selectedFile.getBaseName();
        SalmonFile item = fileItemList.get(position);
        return openListItem.apply(item);
    }

    public void goBack() {
        if (fileManagerMode == Mode.Search && fileCommander.isFileSearcherRunning()) {
            fileCommander.stopFileSearch();
        } else if (fileManagerMode == Mode.Search) {
            executor.execute(() ->
            {
                fileManagerMode = Mode.Browse;
                salmonFiles = currDir.listFiles();
                populateFileList(null);
            });
        } else if (currDir != null && currDir.getParent() != null) {
            SalmonFile finalParent = currDir.getParent();
            executor.execute(() ->
            {
                if (checkFileSearcher())
                    return;
                SalmonFile parentDir = currDir;
                currDir = finalParent;
                salmonFiles = currDir.listFiles();
                populateFileList(parentDir);
            });
        } else {
            SalmonDialogs.promptExit();
        }
    }

    private SalmonFile findCurrentItem(SalmonFile currentFile) {
        if (currentFile == null)
            return null;
        for (SalmonFile file : fileItemList) {
            if (file.getRealFile().getPath().equals(currentFile.getRealFile().getPath())) {
                selectedFiles.clear();
                selectedFiles.add(file);
                return file;
            }
        }
        return null;
    }

    @Override
    public HashSet<BiConsumer<Object, String>> getObservers() {
        return observers;
    }

    public void renameFile(SalmonFile file, String newFilename)
            throws SalmonSecurityException, SalmonIntegrityException, SalmonSequenceException,
            IOException, SalmonRangeExceededException, SalmonAuthException {
        fileCommander.renameFile(file, newFilename);
    }

    public enum Mode {
        Browse, Search, Copy, Move
    }

    public void exportFiles(SalmonFile[] items, Consumer<IRealFile[]> onFinished, boolean deleteSource) {
        executor.execute(() ->
        {
            setFileProgress(0);
            setFilesProgress(0);
            setTaskRunning(true);

            Exception[] exception = new Exception[]{null};
            int[] processedFiles = new int[]{-1};
            IRealFile[] files = null;
            List<SalmonFile> failedFiles = new ArrayList<>();
            try {
                files = fileCommander.exportFiles(items,
                        SalmonDriveManager.getDrive().getExportDir(),
                        deleteSource, true,
                        (taskProgress) ->
                        {
                            if (processedFiles[0] < taskProgress.getProcessedFiles()) {
                                try {
                                    setTaskMessage("Exporting: " + taskProgress.getFile().getBaseName()
                                            + " " + (taskProgress.getProcessedFiles() + 1)
                                            + "/" + taskProgress.getTotalFiles());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                processedFiles[0] = taskProgress.getProcessedFiles();
                            }
                            setFileProgress(taskProgress.getProcessedBytes() / (double) taskProgress.getTotalBytes());
                            setFilesProgress(taskProgress.getProcessedFiles() / (double) taskProgress.getTotalFiles());
                        }, IRealFile.autoRename, (file, ex) ->
                        {
                            failedFiles.add(file);
                            exception[0] = ex;
                        });
                if (onFinished != null)
                    onFinished.accept(files);
            } catch (Exception e) {
                e.printStackTrace();
                SalmonDialog.promptDialog("Error", "Error while exporting files: " + e.getMessage());
            }
            if (fileCommander.areJobsStopped())
                setTaskMessage("Export Stopped");
            else if (failedFiles.size() > 0)
                SalmonDialog.promptDialog("Export", "Some files failed: " + exception[0].getMessage());
            else if (files != null) {
                setTaskMessage("Export Complete");
                SalmonDialog.promptDialog("Export", "Files Exported To: "
                        + SalmonDriveManager.getDrive().getExportDir().getAbsolutePath());
            }
            setFileProgress(1);
            setFilesProgress(1);

            setTaskRunning(false);
        });
    }

    public void importFiles(IRealFile[] fileNames, SalmonFile importDir, boolean deleteSource,
                            Consumer<SalmonFile[]> onFinished) {
        executor.execute(() ->
        {
            setFileProgress(0);
            setFilesProgress(0);

            setTaskRunning(true);

            final Exception[] exception = {null};
            int[] processedFiles = new int[]{-1};
            SalmonFile[] files = null;
            List<IRealFile> failedFiles = new ArrayList<>();
            try {
                files = fileCommander.importFiles(fileNames, importDir,
                        deleteSource, true,
                        (taskProgress) ->
                        {
                            if (processedFiles[0] < taskProgress.getProcessedFiles()) {
                                try {
                                    setTaskMessage("Importing: " + taskProgress.getFile().getBaseName()
                                            + " " + (taskProgress.getProcessedFiles() + 1) + "/" + taskProgress.getTotalFiles());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                processedFiles[0] = taskProgress.getProcessedFiles();
                            }
                            setFileProgress(taskProgress.getProcessedBytes() / (double) taskProgress.getTotalBytes());
                            setFilesProgress(taskProgress.getProcessedFiles() / (double) taskProgress.getTotalFiles());
                        }, IRealFile.autoRename, (file, ex) ->
                        {
                            handleThrowException(ex);
                            failedFiles.add(file);
                            exception[0] = ex;
                        });
                onFinished.accept(files);
            } catch (Exception e) {
                e.printStackTrace();
                if (!handleException(e)) {
                    SalmonDialog.promptDialog("Error", "Error while importing files: " + e.getMessage());
                }
            }
            if (fileCommander.areJobsStopped())
                setTaskMessage("Import Stopped");
            else if (failedFiles.size() > 0)
                SalmonDialog.promptDialog("Import", "Some files failed: " + exception[0].getMessage());
            else if (files != null)
                setTaskMessage("Import Complete");
            setFileProgress(1);
            setFilesProgress(1);
            setTaskRunning(false);
        });
    }

    public void handleThrowException(Exception ex) {
    }

    public void search(String value, boolean any) {
        searchTerm = value;
        if (checkFileSearcher())
            return;
        executor.execute(() ->
        {
            fileManagerMode = Mode.Search;
            setFileProgress(0);
            setFilesProgress(0);
            try {
                if (currDir.getPath() != null)
                    setPathText(currDir.getPath() + "?search=" + value);
            } catch (Exception e) {
                e.printStackTrace();
            }
            salmonFiles = new SalmonFile[]{};
            populateFileList(null);
            setTaskRunning(true);
            setStatus("Searching");
            salmonFiles = fileCommander.search(currDir, value, any, (SalmonFile salmonFile) ->
            {
                int position = 0;
                for (SalmonFile file : fileItemList) {
                    if (salmonFile.getTag() != null &&
                            (file.getTag() == null || (int) salmonFile.getTag() > (int) file.getTag()))
                        break;
                    position++;
                }
                try {
                    fileItemList.add(position, salmonFile);
                    onFileItemAdded.accept(position, salmonFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, null);
            if (!fileCommander.isFileSearcherStopped())
                setStatus("Search Complete");
            else
                setStatus("Search Stopped");
            setTaskRunning(false);
        });
    }

    public void createVault(String dirPath, String password)
            throws SalmonSecurityException, SalmonIntegrityException, SalmonSequenceException,
            IOException, SalmonAuthException {
        SalmonDriveManager.createDrive(dirPath, password);
        SalmonSettings.getInstance().setVaultLocation(dirPath);
        SalmonPreferences.savePrefs();
        currDir = SalmonDriveManager.getDrive().getVirtualRoot();
        refresh();
    }

    public void clearCopiedFiles() {
        copyFiles = null;
        fileManagerMode = Mode.Browse;
        setTaskRunning(false, false);
        setTaskMessage("");
    }

    public String getFileProperties(SalmonFile item)
            throws SalmonSecurityException, SalmonIntegrityException, IOException, SalmonAuthException {
        return "Name: " + item.getBaseName() + "\n" +
                "Path: " + item.getPath() + "\n" +
                (!item.isDirectory() ? ("Size: " + ByteUtils.getBytes(item.getSize(), 2)
                        + " (" + item.getSize() + " bytes)") : "Items: " + item.listFiles().length) + "\n" +
                "Encrypted Name: " + item.getRealFile().getBaseName() + "\n" +
                "Encrypted Path: " + item.getRealFile().getAbsolutePath() + "\n" +
                (!item.isDirectory() ? "Encrypted Size: " + ByteUtils.getBytes(item.getRealFile().length(), 2)
                        + " (" + item.getRealFile().length() + " bytes)" : "") + "\n";
    }
}