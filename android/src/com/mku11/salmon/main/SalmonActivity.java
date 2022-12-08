package com.mku11.salmon.main;
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

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.arch.core.util.Function;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mku.android.salmonvault.R;
import com.mku11.salmon.SalmonGenerator;
import com.mku11.salmon.file.AndroidDrive;
import com.mku11.salmon.file.AndroidSharedFileObserver;
import com.mku11.salmon.media.SalmonMediaDataSource;
import com.mku11.salmon.streams.SalmonStream;
import com.mku11.salmon.utils.FileCommander;
import com.mku11.salmon.utils.FileUtils;
import com.mku11.salmon.utils.Utils;
import com.mku11.salmonfs.IRealFile;
import com.mku11.salmonfs.SalmonAuthException;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;
import com.mku11.salmonfs.SalmonFileExporter;
import com.mku11.salmonfs.SalmonFileImporter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class SalmonActivity extends AppCompatActivity {
    private static final String TAG = SalmonActivity.class.getName();
    private static final long MAX_FILE_SIZE_TO_SHARE = 100 * 1024 * 1024;
    private static final long MEDIUM_FILE_SIZE_TO_SHARE = 10 * 1024 * 1024;

    private static final int BUFFER_SIZE = 512 * 1024;
    private static final int THREADS = 4;

    public static SalmonFile rootDir;
    public static SalmonFile currDir;
    private FileCommander fileCommander;
    private SalmonFile[] copyFiles;

    public SalmonFile[] getSalmonFiles() {
        return salmonFiles;
    }

    enum MediaType {
        AUDIO, VIDEO
    }

    enum Action {
        REFRESH, IMPORT, VIEW, EDIT, SHARE,
        EXPORT, DELETE, RENAME,
        MULTI_SELECT, SINGLE_SELECT, SELECT_ALL, UNSELECT_ALL,
        EXPORT_SELECTED, DELETE_SELECTED, COPY, MOVE, PASTE,
        NEW_FOLDER, SEARCH, STOP, SORT,
        PROPERTIES, SETTINGS, ABOUT, EXIT
    }


    Semaphore done = new Semaphore(1);
    Comparator<SalmonFile> defaultComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else return 0;
    };
    Comparator<SalmonFile> filenameComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return tryGetBasename(c1).compareTo(tryGetBasename(c2));
    };
    Comparator<SalmonFile> sizeComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return (int) (tryGetSize(c1) - tryGetSize(c2));
    };
    Comparator<SalmonFile> typeComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return tryGetType(c1).compareTo(tryGetType(c2));
    };
    Comparator<SalmonFile> dateComparator = (SalmonFile c1, SalmonFile c2) ->
    {
        if (c1.isDirectory() && !c2.isDirectory())
            return -1;
        else if (!c1.isDirectory() && c2.isDirectory())
            return 1;
        else
            return (int) (tryGetDate(c1) - tryGetDate(c2));
    };
    Comparator<SalmonFile> relevanceComparator = (SalmonFile c1, SalmonFile c2) ->
            (int) c2.getTag() - (int) c1.getTag();
    private Mode mode = Mode.Browse;
    private View statusControlLayout;
    private TextView statusText;
    private ProgressBar fileProgress;
    private ProgressBar filesProgress;
    private TextView pathText;
    private RecyclerView gridList;
    private FileGridAdapter adapter;

    private final ArrayList<SalmonFile> fileItemList = new ArrayList<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private SortType sortType = SortType.Name;
    private SalmonFile[] salmonFiles;

    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.main);
        setupWindow();
        setupControls();
        setupFileCommander();
        setupListeners();
        loadSettings();
        setupVirtualDrive();
        setupRootDir();
        setupNativeLib();
    }

    private void setupNativeLib() {
        System.loadLibrary("salmon");
    }

    private void setupFileCommander() {
        fileCommander = new FileCommander(BUFFER_SIZE, THREADS);
    }

    private void loadSettings() {
        SalmonStream.setProviderType(SettingsActivity.getProviderType(this));
        SalmonGenerator.setPbkdfType(SettingsActivity.getPbkdfType(this));
        SalmonFileExporter.setEnableLog(SettingsActivity.getEnableLog(this));
        SalmonFileExporter.setEnableLogDetails(SettingsActivity.getEnableLogDetails(this));
        SalmonFileImporter.setEnableLog(SettingsActivity.getEnableLog(this));
        SalmonFileImporter.setEnableLogDetails(SettingsActivity.getEnableLogDetails(this));
        SalmonStream.setEnableLogDetails(SettingsActivity.getEnableLog(this));
        SalmonMediaDataSource.setEnableLog(SettingsActivity.getEnableLog(this));
        Utils.removeFromRecents(this, SettingsActivity.getExcludeFromRecents(this));
    }

    private void setupWindow() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
    }


    private void setupListeners() {
        fileCommander.setFileImporterOnTaskProgressChanged((Object sender, long bytesRead, long totalBytesRead, String message) ->
                runOnUiThread(() -> {
                    statusText.setText(message);
                    fileProgress.setProgress((int) (bytesRead * 100.0F / totalBytesRead));
                }));

        fileCommander.setFileExporterOnTaskProgressChanged((Object sender, long bytesWritten, long totalBytesWritten, String message) ->
                runOnUiThread(() -> {
                    statusText.setText(message);
                    fileProgress.setProgress((int) (bytesWritten * 100.0F / totalBytesWritten));
                }));
    }

    private void setupControls() {
        fileProgress = findViewById(R.id.fileProgress);
        filesProgress = findViewById(R.id.filesProgress);
        statusText = findViewById(R.id.status);
        ImageButton cancelButton = findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener((view) -> {
            fileCommander.cancelJobs();
            mode = Mode.Browse;
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    showTaskRunning(false), 1000);
        });
        statusControlLayout = findViewById(R.id.statusControlLayout);
        pathText = findViewById(R.id.path);
        pathText.setText("");
        gridList = findViewById(R.id.gridList);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 4, GridLayoutManager.VERTICAL, false);
        gridList.setLayoutManager(gridLayoutManager);
        registerForContextMenu(gridList);
        adapter = createAdapter();
        gridList.setAdapter(adapter);

    }

    private FileGridAdapter createAdapter() {
        return new FileGridAdapter(this, fileItemList, (Integer pos) ->
        {
            try {
                return selected(pos);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return null;
        });
    }

    protected void setupRootDir() {
        String vaultLocation = SettingsActivity.getVaultLocation(this);
        try {
            SalmonDriveManager.setDriveLocation(vaultLocation);
            SalmonDriveManager.getDrive().setEnableIntegrityCheck(true);
            rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            currDir = rootDir;
            if (rootDir == null) {
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
                if (mode == Mode.Browse)
                    salmonFiles = currDir.listFiles();
                displayFiles(false);
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
            Toast.makeText(this, getString(R.string.AnotherProcessRunning), Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }

    private void displayFiles(boolean reset) {
        runOnUiThread(() -> {
            try {
                pathText.setText(currDir.getPath());
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            fileItemList.clear();
            if (reset) {
                adapter.resetCache(gridList);
            }
            adapter.notifyDataSetChanged();
            Collections.addAll(fileItemList, salmonFiles);
            if (mode == Mode.Browse)
                sortFiles(SortType.Default);
            adapter.notifyDataSetChanged();
        });
    }

    private void setupVirtualDrive() {
        try {
            SalmonDriveManager.setVirtualDriveClass(AndroidDrive.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(0, Action.REFRESH.ordinal(), 0, getString(R.string.Refresh))
                .setIcon(android.R.drawable.ic_menu_rotate)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, Action.IMPORT.ordinal(), 0, getString(R.string.Import))
                .setIcon(android.R.drawable.ic_menu_add)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, Action.NEW_FOLDER.ordinal(), 0, getString(R.string.NewFolder))
                .setIcon(android.R.drawable.ic_input_add);
        if (adapter.getMode() == FileGridAdapter.Mode.MULTI_SELECT) {
            menu.add(0, Action.EXPORT_SELECTED.ordinal(), 0, getString(R.string.Export))
                    .setIcon(android.R.drawable.btn_minus);
            menu.add(0, Action.COPY.ordinal(), 0, getString(R.string.Copy))
                    .setIcon(android.R.drawable.ic_menu_send);
            menu.add(0, Action.MOVE.ordinal(), 0, getString(R.string.Move))
                    .setIcon(android.R.drawable.ic_menu_send);
            menu.add(0, Action.DELETE_SELECTED.ordinal(), 0, getString(R.string.Delete))
                    .setIcon(android.R.drawable.ic_menu_delete);

            menu.add(0, Action.SELECT_ALL.ordinal(), 0, getString(R.string.SelectAll))
                    .setIcon(android.R.drawable.checkbox_on_background);

            menu.add(0, Action.UNSELECT_ALL.ordinal(), 0, getString(R.string.UnselectAll))
                    .setIcon(android.R.drawable.checkbox_off_background);
        }

        if (mode == Mode.Copy || mode == Mode.Move)
            menu.add(0, Action.PASTE.ordinal(), 0, getString(R.string.Paste))
                    .setIcon(android.R.drawable.ic_menu_set_as);

        menu.add(0, Action.SEARCH.ordinal(), 0, getString(R.string.Search))
                .setIcon(android.R.drawable.ic_menu_search);
        if (fileCommander.isRunning())
            menu.add(0, Action.STOP.ordinal(), 0, getString(R.string.Stop))
                    .setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        menu.add(0, Action.SORT.ordinal(), 0, getString(R.string.Sort))
                .setIcon(android.R.drawable.ic_menu_sort_alphabetically);
        if (adapter.getMode() == FileGridAdapter.Mode.SINGLE_SELECT)
            menu.add(0, Action.MULTI_SELECT.ordinal(), 0, getString(R.string.MultiSelect))
                    .setIcon(android.R.drawable.ic_menu_agenda);
        else
            menu.add(0, Action.SINGLE_SELECT.ordinal(), 0, getString(R.string.SingleSelect))
                    .setIcon(android.R.drawable.ic_menu_agenda);
        menu.add(0, Action.SETTINGS.ordinal(), 0, getString(R.string.Settings))
                .setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(0, Action.ABOUT.ordinal(), 0, getString(R.string.About))
                .setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(0, Action.EXIT.ordinal(), 0, getString(R.string.Exit))
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel);

        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (Action.values()[item.getItemId()]) {
            case REFRESH:
                refresh();
                return true;
            case IMPORT:
                promptImportFiles();
                return true;
            case EXPORT_SELECTED:
                exportSelectedFiles();
                return true;
            case NEW_FOLDER:
                promptNewFolder();
                return true;
            case COPY:
                mode = Mode.Copy;
                copyFiles = adapter.getSelectedFiles().toArray(new SalmonFile[0]);
                showTaskRunning(true, false);
                showTaskMessage(copyFiles.length + " " + getResources().getString(R.string.ItemsSelectedForMove));
                adapter.setMultiSelect(false);
                return true;
            case MOVE:
                mode = Mode.Move;
                copyFiles = adapter.getSelectedFiles().toArray(new SalmonFile[0]);
                showTaskRunning(true, false);
                showTaskMessage(copyFiles.length + " " + getResources().getString(R.string.ItemsSelectedForMove));
                adapter.setMultiSelect(false);
                return true;
            case DELETE_SELECTED:
                deleteSelectedFiles();
                return true;
            case PASTE:
                pasteSelected();
                return true;
            case SELECT_ALL:
                selectAll(true);
                return true;
            case UNSELECT_ALL:
                selectAll(false);
                return true;
            case SEARCH:
                promptSearch();
                return true;
            case MULTI_SELECT:
                adapter.setMultiSelect(true);
                return true;
            case SINGLE_SELECT:
                adapter.setMultiSelect(false);
                return true;
            case STOP:
                fileCommander.cancelJobs();
                return true;
            case SORT:
                promptSortFiles();
                break;
            case SETTINGS:
                startSettings();
                return true;
            case ABOUT:
                about();
                return true;
            case EXIT:
                exit();
                return true;
        }
        super.onOptionsItemSelected(item);
        return false;
    }

    private void pasteSelected() {
        copySelectedFiles(mode == Mode.Move);
    }

    private void selectAll(boolean value) {
        adapter.selectAll(value);
    }

    private void promptSearch() {
        ActivityCommon.promptEdit(this, getString(R.string.Search), "Keywords", "", getString(R.string.MatchAnyTerm), this::search);
    }

    public void showTaskRunning(boolean value) {
        showTaskRunning(value, true);
    }

    public void showTaskRunning(boolean value, boolean progress) {
        runOnUiThread(() -> {
            fileProgress.setProgress(0);
            filesProgress.setProgress(0);
            statusControlLayout.setVisibility(value ? View.VISIBLE : View.GONE);
            if (progress) {
                fileProgress.setVisibility(value ? View.VISIBLE : View.GONE);
                filesProgress.setVisibility(value ? View.VISIBLE : View.GONE);
            } else {
                fileProgress.setVisibility(View.GONE);
                filesProgress.setVisibility(View.GONE);
            }
            if (!value)
                statusText.setText("");
        });
    }

    public void showTaskMessage(String msg) {
        runOnUiThread(() -> statusText.setText(msg == null ? "" : msg));
    }

    private void sortFiles(SortType sortType) {
        this.sortType = sortType;
        switch (sortType) {
            case Default:
                Collections.sort(fileItemList, defaultComparator);
                break;
            case Name:
                Collections.sort(fileItemList, filenameComparator);
                break;
            case Size:
                Collections.sort(fileItemList, sizeComparator);
                break;
            case Type:
                Collections.sort(fileItemList, typeComparator);
                break;
            case Date:
                Collections.sort(fileItemList, dateComparator);
                break;
        }
    }

    private String tryGetBasename(SalmonFile salmonFile) {
        try {
            return salmonFile.getBaseName();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    private String tryGetType(SalmonFile salmonFile) {
        try {
            if (salmonFile.isDirectory())
                return salmonFile.getBaseName();
            return SalmonDriveManager.getDrive().getExtensionFromFileName(salmonFile.getBaseName()).toLowerCase();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    private long tryGetSize(SalmonFile salmonFile) {
        try {
            if (salmonFile.isDirectory())
                return salmonFile.listFiles().length;
            return salmonFile.getSize();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    private long tryGetDate(SalmonFile salmonFile) {
        try {
            return salmonFile.getLastDateTimeModified();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    private void about() {
        ActivityCommon.promptDialog(this, getString(R.string.About),
                getString(R.string.app_name) + " v" + SalmonApplication.getVersion() + "\n"
                + getString(R.string.AboutText),
                getString(R.string.GetSourceCode), (DialogInterface dialog, int which) ->
                {
                    Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(getString(R.string.SourceCodeURL)));
                    startActivity(intent);
                },
                getString(android.R.string.ok), null);
    }

    private void promptImportFiles() {
        ((AndroidDrive) SalmonDriveManager.getDrive()).pickFiles(this, getString(R.string.SelectFilesToImport), false,
                SettingsActivity.getVaultLocation(this));
    }

    private void promptNewFolder() {
        ActivityCommon.promptEdit(this, getString(R.string.NewFolder), getString(R.string.FolderName),
                "New Folder", null, (String folderName, Boolean checked) -> {
                    try {
                        currDir.createDirectory(folderName);
                        refresh();
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        Toast.makeText(SalmonActivity.this,
                                getString(R.string.CouldNotCreateFolder) + " "
                                        + exception.getMessage(), Toast.LENGTH_LONG).show();
                        refresh();
                    }
                }
        );
    }

    private void promptSortFiles() {
        List<String> sortTypes = new ArrayList<>();
        for (SortType type : SortType.values()) {
            sortTypes.add(type.name());
        }
        ActivityCommon.promptSingleValue(this, getString(R.string.Sort),
                sortTypes, sortTypes.indexOf(sortType.toString()), (DialogInterface dialog, int which) -> {
                    sortFiles(SortType.values()[which]);
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                }
        );
    }

    private void exit() {
        finish();
    }

    private void startSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void deleteSelectedFiles() {
        deleteFiles(adapter.getSelectedFiles().toArray(new SalmonFile[0]));
    }

    private void copySelectedFiles(boolean move) {
        copyFiles(copyFiles, currDir, move);
    }

    private void deleteFiles(SalmonFile[] files) {
        executor.submit(() ->
        {
            showTaskRunning(true);
            try {
                fileCommander.doDeleteFiles((file) -> {
                    runOnUiThread(() -> {
                        fileItemList.remove(file);
                        sortFiles(sortType);
                        adapter.notifyDataSetChanged();
                    });
                    return null;
                }, files);
            } catch (Exception e) {
                e.printStackTrace();
            }
            runOnUiThread(() -> {
                fileProgress.setProgress(100);
                filesProgress.setProgress(100);
            });
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    showTaskRunning(false), 1000);
        });
    }

    private void copyFiles(SalmonFile[] files, SalmonFile dir, boolean move) {
        executor.submit(() ->
        {
            showTaskRunning(true);
            try {
                fileCommander.doCopyFiles(files, dir, move, (fileInfo) -> {
                    runOnUiThread(() -> {
                        fileProgress.setProgress(fileInfo.fileProgress);
                        filesProgress.setProgress((int) (fileInfo.processedFiles * 100F / fileInfo.totalFiles));
                        String action = move ? " Moving: " : " Copying: ";
                        showTaskMessage((fileInfo.processedFiles + 1) + "/" + fileInfo.totalFiles + action + fileInfo.filename);
                    });
                    return null;
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            runOnUiThread(() -> {
                fileProgress.setProgress(100);
                filesProgress.setProgress(100);
                refresh();
            });
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    showTaskRunning(false), 1000);
            copyFiles = null;
            mode = Mode.Browse;
        });
    }

    private void exportSelectedFiles() {
        if (rootDir == null || !SalmonDriveManager.getDrive().isAuthenticated())
            return;
        exportFiles(adapter.getSelectedFiles().toArray(new SalmonFile[0]), (files) ->
        {
            refresh();
            return null;
        });
    }

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        menu.setHeaderTitle(getString(R.string.Action));
        menu.add(0, Action.VIEW.ordinal(), 0, getString(R.string.ViewExternal))
                .setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, Action.EDIT.ordinal(), 0, getString(R.string.EditExternal))
                .setIcon(android.R.drawable.ic_menu_send);
        menu.add(0, Action.SHARE.ordinal(), 0, getString(R.string.ShareExternal))
                .setIcon(android.R.drawable.ic_menu_send);
        menu.add(0, Action.EXPORT.ordinal(), 0, getString(R.string.Export))
                .setIcon(android.R.drawable.btn_minus);
        menu.add(0, Action.COPY.ordinal(), 0, getString(R.string.Copy))
                .setIcon(android.R.drawable.ic_menu_delete);
        menu.add(0, Action.MOVE.ordinal(), 0, getString(R.string.Move))
                .setIcon(android.R.drawable.ic_menu_delete);
        menu.add(0, Action.DELETE.ordinal(), 0, getString(R.string.Delete))
                .setIcon(android.R.drawable.ic_menu_delete);
        menu.add(0, Action.RENAME.ordinal(), 0, getString(R.string.Rename))
                .setIcon(android.R.drawable.ic_menu_edit);
        menu.add(0, Action.PROPERTIES.ordinal(), 0, getString(R.string.Properties))
                .setIcon(android.R.drawable.ic_dialog_info);
    }

    public boolean onContextItemSelected(MenuItem item) {
        int position = adapter.getPosition();
        SalmonFile ifile = fileItemList.get(position);
        switch (Action.values()[item.getItemId()]) {
            case VIEW:
                openWith(ifile, Action.VIEW.ordinal());
                break;
            case EDIT:
                openWith(ifile, Action.EDIT.ordinal());
                break;
            case SHARE:
                openWith(ifile, Action.SHARE.ordinal());
                break;
            case EXPORT:
                exportFile(ifile, position);
                break;
            case COPY:
                mode = Mode.Copy;
                copyFiles = new SalmonFile[]{ifile};
                showTaskRunning(true, false);
                showTaskMessage(copyFiles.length + " " + getResources().getString(R.string.ItemsSelectedForCopy));
                break;
            case MOVE:
                mode = Mode.Move;
                copyFiles = new SalmonFile[]{ifile};
                showTaskRunning(true, false);
                showTaskMessage(copyFiles.length + " " + getResources().getString(R.string.ItemsSelectedForMove));
                break;
            case DELETE:
                deleteFile(ifile, position);
                break;
            case RENAME:
                renameFile(ifile, position);
                break;
            case PROPERTIES:
                showProperties(ifile);
                break;
        }
        return true;
    }

    private void showProperties(SalmonFile ifile) {
        try {
            ActivityCommon.promptDialog(this, getString(R.string.Properties),
                    getString(R.string.Name) + ": " + ifile.getBaseName() + "\n" +
                            getString(R.string.Path) + ": " + ifile.getPath() + "\n" +
                            getString(R.string.Size) + ": " + Utils.getBytes(ifile.getSize(), 2) + " (" + ifile.getSize() + " bytes)" + "\n" +
                            "\n" +
                            getString(R.string.EncryptedName) + ": " + ifile.getRealFile().getBaseName() + "\n" +
                            getString(R.string.EncryptedPath) + ": " + ifile.getRealFile().getAbsolutePath() + "\n" +
                            getString(R.string.EncryptedSize) + ": " + Utils.getBytes(ifile.getRealFile().length(), 2) + " (" + ifile.getRealFile().length() + " bytes)" + "\n"
                    , getString(android.R.string.ok), null,
                    null, null
            );
        } catch (Exception exception) {
            Toast.makeText(this, getString(R.string.CouldNotGetFileProperties), Toast.LENGTH_LONG).show();
            exception.printStackTrace();
        }
    }

    private void deleteFile(SalmonFile ifile, int position) {
        deleteFiles(new SalmonFile[]{ifile});
        runOnUiThread(() -> {
            fileItemList.remove(ifile);
            adapter.notifyItemRemoved(position);
        });
    }

    private void renameFile(SalmonFile ifile, int position) {
        runOnUiThread(() -> {
            try {
                ActivityCommon.promptEdit(SalmonActivity.this,
                        getString(R.string.Rename), getString(R.string.NewFilename),
                        ifile.getBaseName(), null, (String newFilename, Boolean checked) -> {
                            try {
                                ifile.rename(newFilename);
                            } catch (Exception exception) {
                                exception.printStackTrace();
                            }
                            adapter.notifyItemChanged(position);
                        });
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    private void exportFile(SalmonFile ifile, int position) {
        if (ifile == null)
            return;

        if (rootDir == null || !SalmonDriveManager.getDrive().isAuthenticated())
            return;

        exportFiles(new SalmonFile[]{ifile}, (IRealFile[] realFiles) ->
        {
            runOnUiThread(() -> {
                fileItemList.remove(ifile);
                adapter.notifyItemRemoved(position);
            });
            return null;
        });
    }

    private void openWith(SalmonFile salmonFile, int action) {
        try {
            if (salmonFile.getSize() > MAX_FILE_SIZE_TO_SHARE) {
                Toast toast = Toast.makeText(this, getString(R.string.FileSizeTooLarge), Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
                return;
            }
            if (salmonFile.getSize() > MEDIUM_FILE_SIZE_TO_SHARE) {
                Toast toast = Toast.makeText(this, getString(R.string.PleaseWaitWhileDecrypting), Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
            new Thread(() ->
            {
                try {
                    chooseApp(salmonFile, action);
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }).start();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void chooseApp(SalmonFile salmonFile, int action) throws Exception {

        File sharedFile = AndroidDrive.copyToSharedFolder(salmonFile);
        sharedFile.deleteOnExit();
        String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(salmonFile.getBaseName()).toLowerCase();
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        android.net.Uri uri = FileProvider.getUriForFile(this, getString(R.string.FileProvider), sharedFile);
        ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(this).setType(mimeType);

        Intent intent;
        // if we just share (final) we can show the android chooser activity
        // since we don't have to grant the app write permissions
        if (action == Action.VIEW.ordinal()) {
            intent = builder.createChooserIntent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(uri);

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent finalIntent1 = intent;
            runOnUiThread(() -> {
                try {
                    startActivity(finalIntent1);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Toast.makeText(SalmonActivity.this, getString(R.string.NoApplicationsFound), Toast.LENGTH_LONG).show();
                }
            });
        } else {

            // we show only apps that explicitly have intent filters for action edit
            if (action == Action.SHARE.ordinal()) {
                builder.setStream(uri);
                intent = builder.getIntent();
                intent.setAction(Intent.ACTION_SEND);
            } else {
                intent = builder.getIntent();
                intent.setAction(Intent.ACTION_EDIT);
                intent.setData(uri);
            }
            // we offer the user a list so they can grant write permissions only to that app
            LinkedHashMap<String, String> apps = getAppsForIntent(intent);
            Intent finalIntent = intent;
            runOnUiThread(() -> {
                ActivityCommon.promptOpenWith(this, finalIntent, apps, uri, sharedFile, salmonFile, action == Action.EDIT.ordinal(),
                        (AndroidSharedFileObserver fileObserver) ->
                        {
                            reimportSharedFile(uri, fileObserver);
                            return null;
                        });
            });
        }
    }

    private LinkedHashMap<String, String> getAppsForIntent(Intent intent) {
        List<ResolveInfo> appInfoList = getPackageManager().queryIntentActivities(intent, 0);
        LinkedHashMap<String, String> apps = new LinkedHashMap<>();
        for (ResolveInfo resolveInfo : appInfoList) {
            //FIXME: the key should be the package name
            String name = getPackageManager().getApplicationLabel(resolveInfo.activityInfo.applicationInfo).toString();
            String packageName = resolveInfo.activityInfo.applicationInfo.packageName;
            apps.put(name, packageName);
        }
        return apps;
    }

    private void reimportSharedFile(android.net.Uri uri, AndroidSharedFileObserver fileObserver) {
        try {
            done.acquire(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (rootDir == null || !SalmonDriveManager.getDrive().isAuthenticated())
            return;
        DocumentFile docFile = DocumentFile.fromSingleUri(SalmonApplication.getInstance().getApplicationContext(), uri);
        IRealFile realFile = AndroidDrive.getFile(docFile);
        if (realFile == null)
            return;
        SalmonFile oldSalmonFile = fileObserver.getSalmonFile();
        SalmonFile parentDir = oldSalmonFile.getParent();

        showTaskRunning(true);
        importFiles(new IRealFile[]{realFile}, parentDir, false, (SalmonFile[] importedSalmonFiles) ->
        {
            fileObserver.setSalmonFile(importedSalmonFiles[0]);
            runOnUiThread(() -> {
                if (importedSalmonFiles[0] != null) {
                    fileItemList.add(importedSalmonFiles[0]);
                    fileItemList.remove(oldSalmonFile);
                    if (oldSalmonFile.exists())
                        oldSalmonFile.delete();
                    sortFiles(sortType);
                    adapter.notifyDataSetChanged();
                }
                Toast.makeText(this, getString(R.string.FileSavedInSalmonVault), Toast.LENGTH_LONG).show();
            });
            done.release(1);
            return null;
        });
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AndroidDrive.REQUEST_SDCARD_CODE_IMPORT_FILE) {
            if (data == null)
                return;
            if (rootDir == null || !SalmonDriveManager.getDrive().isAuthenticated())
                return;
            try {
                IRealFile[] filesToImport = AndroidDrive.getFilesFromIntent(this, data);
                importFiles(filesToImport, currDir, SettingsActivity.getDeleteAfterImport(this), (SalmonFile[] importedFiles) ->
                {
                    runOnUiThread(this::refresh);
                    return null;
                });
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, getString(R.string.CouldNotImportFiles), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == AndroidDrive.REQUEST_SDCARD_VAULT_FOLDER) {
            if (data != null) {
                boolean res = ActivityCommon.setVaultFolder(this, data);
                if (!res) {
                    promptSelectRoot();
                    return;
                }
                clear();
                setupRootDir();
            }
        }
    }

    public void clear() {
        logout();
        rootDir = null;
        currDir = null;
        runOnUiThread(() -> {
            fileItemList.clear();
            adapter.resetCache(gridList);
            adapter.notifyDataSetChanged();
        });
    }

    private void checkCredentials() {
        if (SalmonDriveManager.getDrive().hasConfig()) {
            ActivityCommon.promptPassword(this, (pass) ->
            {
                try {
                    rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
                    currDir = rootDir;
                } catch (SalmonAuthException e) {
                    e.printStackTrace();
                }
                refresh();
                return null;
            });
        } else {
            ActivityCommon.promptSetPassword(this, (String pass) ->
            {
                try {
                    rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
                    currDir = rootDir;
                } catch (SalmonAuthException e) {
                    e.printStackTrace();
                }
                refresh();
                if (fileItemList.size() == 0)
                    promptImportFiles();
                return null;
            });
        }
    }

    protected boolean selected(int position) throws Exception {
        SalmonFile selectedFile = fileItemList.get(position);
        if (selectedFile.isDirectory()) {
            executor.submit(() -> {
                if (checkFileSearcher())
                    return;
                currDir = selectedFile;
                salmonFiles = currDir.listFiles();
                displayFiles(true);
            });
            return true;
        }
        String filename = selectedFile.getBaseName();


        if (FileUtils.isVideo(filename)) {
            startMediaPlayer(position, MediaType.VIDEO);
            return true;
        } else if (FileUtils.isAudio(filename)) {
            startMediaPlayer(position, MediaType.AUDIO);
            return true;
        } else if (FileUtils.isImage(filename)) {
            startWebViewer(position);
            return true;
        } else if (FileUtils.isText(filename)) {
            startWebViewer(position);
            return true;
        }
        return false;
    }

    private void logout() {
        try {
            SalmonDriveManager.getDrive().authenticate(null);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void promptSelectRoot() {
        ((AndroidDrive) SalmonDriveManager.getDrive()).pickFiles(this, getString(R.string.SelectFolderForFiles), true,
                SettingsActivity.getVaultLocation(this));
    }

    public void startMediaPlayer(int position, MediaType type) {
        ArrayList<SalmonFile> salmonFiles = new ArrayList<>();
        int pos = 0;
        for (int i = 0; i < fileItemList.size(); i++) {
            SalmonFile selectedFile = fileItemList.get(i);
            String filename;
            try {
                filename = selectedFile.getBaseName();
                if ((type == MediaType.VIDEO && FileUtils.isVideo(filename))
                        || (type == MediaType.AUDIO && FileUtils.isAudio(filename))
                ) {
                    salmonFiles.add(selectedFile);
                }
                if (i == position)
                    pos = salmonFiles.size() - 1;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Intent intent = new Intent(this, MediaPlayerActivity.class);
        MediaPlayerActivity.setMediaFiles(pos, salmonFiles.toArray(new SalmonFile[0]));
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startWebViewer(int position) {
        ArrayList<SalmonFile> salmonFiles = new ArrayList<>();
        int pos = 0;
        for (int i = 0; i < fileItemList.size(); i++) {
            SalmonFile selectedFile = fileItemList.get(i);
            String filename;
            try {
                filename = selectedFile.getBaseName();
                if (FileUtils.isImage(filename) || FileUtils.isText(filename)) {
                    salmonFiles.add(selectedFile);
                }
                if (i == position)
                    pos = salmonFiles.size() - 1;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Intent intent = new Intent(this, WebViewerActivity.class);
        WebViewerActivity.setContentFiles(pos, salmonFiles.toArray(new SalmonFile[0]));
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    protected void onDestroy() {
        logout();
        Utils.removeFromRecents(this, true);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        SalmonFile parent = currDir.getParent();
        if (adapter.getMode() == FileGridAdapter.Mode.MULTI_SELECT) {
            adapter.setMultiSelect(false);
        } else if (mode == Mode.Search && fileCommander.isFileSearcherRunning()) {
            fileCommander.stopFileSearch();
        } else if (mode == Mode.Search) {
            executor.submit(() -> {
                mode = Mode.Browse;
                salmonFiles = currDir.listFiles();
                displayFiles(true);
            });
        } else if (parent != null) {
            executor.submit(() -> {
                if (checkFileSearcher())
                    return;
                currDir = parent;
                salmonFiles = currDir.listFiles();
                displayFiles(true);
            });
        } else {
            ActivityCommon.promptDialog(this, getString(R.string.Exit), getString(R.string.ExitApp),
                    getString(android.R.string.ok), (DialogInterface dialog, int which) -> {
                        finish();
                    }, getString(android.R.string.cancel), (DialogInterface dialog, int which) -> {
                        dialog.dismiss();
                    }
            );
        }
    }

    enum Mode {
        Browse, Search, Copy, Move
    }

    private enum SortType {
        Default, Name, Size, Type, Date
    }

    public void exportFiles(SalmonFile[] items, final Function<IRealFile[], Void> OnFinished) {

        executor.submit(() ->
        {
            showTaskRunning(true);
            boolean success = false;
            try {
                success = fileCommander.doExportFiles(items,
                        (progress) -> {
                            runOnUiThread(() -> filesProgress.setProgress(progress));
                            return null;
                        }, OnFinished);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (fileCommander.isStopped())
                showTaskMessage(getString(R.string.ExportStopped));
            else if (!success)
                showTaskMessage(getString(R.string.ExportFailed));
            else showTaskMessage(getString(R.string.ExportComplete));
            runOnUiThread(() -> {
                fileProgress.setProgress(100);
                filesProgress.setProgress(100);
                ActivityCommon.promptDialog(this, getString(R.string.Export), getString(R.string.FilesExportedTo) + SalmonDriveManager.getDrive().getExportDir().getAbsolutePath(),
                        getString(android.R.string.ok), null, null, null);
            });
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    showTaskRunning(false), 1000);

        });
    }

    public void importFiles(IRealFile[] fileNames, SalmonFile importDir, boolean deleteSource,
                            Function<SalmonFile[], Void> OnFinished) {

        executor.submit(() ->
        {
            showTaskRunning(true);
            boolean success = false;
            try {
                success = fileCommander.doImportFiles(fileNames, importDir, deleteSource,
                        (progress) -> {
                            runOnUiThread(() -> filesProgress.setProgress(progress));
                            return null;
                        }, OnFinished);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (fileCommander.isStopped())
                showTaskMessage(getString(R.string.ImportStopped));
            else if (!success)
                showTaskMessage(getString(R.string.ImportFailed));
            else showTaskMessage(getString(R.string.ImportComplete));
            runOnUiThread(() -> {
                fileProgress.setProgress(100);
                filesProgress.setProgress(100);
            });
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    showTaskRunning(false), 1000);
        });
    }


    //TODO: refactor to a class and update ui frequently with progress
    private void search(String value, boolean any) {
        if (checkFileSearcher())
            return;
        executor.submit(() -> {
            mode = SalmonActivity.Mode.Search;
            runOnUiThread(() -> {
                try {
                    pathText.setText(getString(R.string.Searching) + ": " + value);
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
                salmonFiles = new SalmonFile[]{};
                displayFiles(true);
            });
            salmonFiles = fileCommander.search(currDir, value, any, (SalmonFile salmonFile) -> {
                runOnUiThread(() -> {
                    int position = 0;
                    for (SalmonFile file : fileItemList) {
                        if ((int) salmonFile.getTag() > (int) file.getTag()) {
                            break;
                        } else
                            position++;
                    }
                    fileItemList.add(position, salmonFile);
                    adapter.notifyItemInserted(position);
                });
            });
            runOnUiThread(() -> {
                if (!fileCommander.isFileSearcherStopped())
                    pathText.setText(getString(R.string.Search) + ": " + value);
                else
                    pathText.setText(getString(R.string.Search) + " " + getString(R.string.Stopped) + ": " + value);
            });
        });
    }
}