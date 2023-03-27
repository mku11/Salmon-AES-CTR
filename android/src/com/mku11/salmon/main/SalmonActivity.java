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
import android.net.Uri;
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
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mku.android.salmonvault.R;
import com.mku11.file.JavaFile;
import com.mku11.salmon.SalmonGenerator;
import com.mku11.salmon.SalmonTextEncryptor;
import com.mku11.salmon.config.Config;
import com.mku11.salmon.file.AndroidDrive;
import com.mku11.salmon.file.AndroidSharedFileObserver;
import com.mku11.salmon.media.SalmonMediaDataSource;
import com.mku11.salmon.streams.SalmonStream;
import com.mku11.salmon.utils.AndroidSequenceParser;
import com.mku11.salmon.utils.Base64;
import com.mku11.salmon.utils.Utils;
import com.mku11.salmonfs.Comparators;
import com.mku11.salmonfs.FileCommander;
import com.mku11.salmonfs.FileSequencer;
import com.mku11.salmonfs.FileUtils;
import com.mku11.salmonfs.IRealFile;
import com.mku11.salmonfs.SalmonAuthException;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;
import com.mku11.salmonfs.SalmonFileExporter;
import com.mku11.salmonfs.SalmonFileImporter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import com.mku11.salmon.func.Consumer;

public class SalmonActivity extends AppCompatActivity {
    public static final String TAG = SalmonActivity.class.getName();
    public static final int REQUEST_OPEN_VAULT_DIR = 1000;
    public static final int REQUEST_CREATE_VAULT_DIR = 1001;
    public static final int REQUEST_IMPORT_FILES = 1002;
    public static final int REQUEST_EXPORT_DIR = 1003;
    public static final int REQUEST_IMPORT_AUTH_FILE = 1004;
    public static final int REQUEST_EXPORT_AUTH_FILE = 1005;
    public static final String SEQUENCER_DIR_NAME = ".salmon";
    public static final String SEQUENCER_FILE_NAME = "config.xml";

    private static final long MAX_FILE_SIZE_TO_SHARE = 50 * 1024 * 1024;
    private static final long MEDIUM_FILE_SIZE_TO_SHARE = 10 * 1024 * 1024;
    private static final int BUFFER_SIZE = 1 * 1024 * 1024;
    private static final int THREADS = 4;

    public SalmonFile rootDir;
    public SalmonFile currDir;
    private ArrayList<SalmonFile> fileItemList = new ArrayList<>();
    private ExecutorService executor = Executors.newFixedThreadPool(1);
    private Semaphore done = new Semaphore(1);

    private TextView pathText;
    private RecyclerView listView;
    private FileAdapter adapter;
    private View statusControlLayout;
    private TextView statusText;
    private PieProgress fileProgress;
    private PieProgress filesProgress;
    private SalmonFile[] salmonFiles;
    private FileCommander fileCommander;
    private SalmonFile[] copyFiles;
    private String exportAuthID;
    private Mode mode = Mode.Browse;
    private SortType sortType = SortType.Default;

    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setupWindow();
        setContentView(R.layout.main);
        setupControls();
        setupFileCommander();
        setupListeners();
        loadSettings();
        setupSalmonManager();
        setupRootDir();
        setupNativeLib();
    }

    private void setupWindow() {
        if(!Config.allowScreenContents)
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
    }

    private void setupNativeLib() {
        if (Config.enableNativeLib)
            System.loadLibrary("salmon");
    }

    private void setupFileCommander() {
        fileCommander = new FileCommander(BUFFER_SIZE, THREADS);
    }

    private void loadSettings() {
        SalmonTextEncryptor.setBase64(new Base64());
        SalmonGenerator.setPbkdfAlgo(Config.pbkdfAlgo);
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

    private void setupListeners() {
        fileCommander.setImporterProgressListener((IRealFile file, long bytesRead, long totalBytesRead, String message) ->
                runOnUiThread(() -> {
                    statusText.setText(message);
                    fileProgress.setProgress((int) (bytesRead * 100.0F / totalBytesRead));
                }));

        fileCommander.setExporterProgressListener((SalmonFile file, long bytesWritten, long totalBytesWritten, String message) ->
                runOnUiThread(() -> {
                    statusText.setText(message);
                    fileProgress.setProgress((int) (bytesWritten * 100.0F / totalBytesWritten));
                }));
    }

    private void setupControls() {
        fileProgress = findViewById(R.id.fileProgress);
        filesProgress = findViewById(R.id.filesProgress);
        statusText = findViewById(R.id.status);
        statusControlLayout = findViewById(R.id.status_control_layout);
        statusControlLayout.setVisibility(View.GONE);
        pathText = findViewById(R.id.path);
        pathText.setText("");
        listView = findViewById(R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
        registerForContextMenu(listView);
        adapter = createAdapter();
        listView.setAdapter(adapter);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(true);
        Objects.requireNonNull(getSupportActionBar()).setDisplayUseLogoEnabled(true);
        getSupportActionBar().setLogo(R.drawable.logo_48x48);
    }

    private FileAdapter createAdapter() {
        return new FileAdapter(this, fileItemList, (Integer pos) ->
        {
            try {
                return openFile(pos);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return false;
        });
    }

    protected void setupRootDir() {
        String vaultLocation = SettingsActivity.getVaultLocation(this);
        try {
            SalmonDriveManager.openDrive(vaultLocation);
            SalmonDriveManager.getDrive().setEnableIntegrityCheck(true);
            rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            currDir = rootDir;
            if (rootDir == null) {
                onOpenVault();
                return;
            }
        } catch (SalmonAuthException e) {
            checkCredentials();
            return;
        } catch (Exception e) {
            e.printStackTrace();
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
                onOpenVault();
                return;
            }
            if (!SalmonDriveManager.getDrive().isAuthenticated()) {
                checkCredentials();
                return;
            }
            executor.submit(() -> {
                if (mode != Mode.Search)
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
                setPath(currDir.getPath());
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            fileItemList.clear();
            if (reset) {
                adapter.resetCache(listView);
            }
            adapter.notifyDataSetChanged();
            Collections.addAll(fileItemList, salmonFiles);
            if (mode == Mode.Browse)
                sortFiles(SortType.Default);
            adapter.notifyDataSetChanged();
        });
    }

    private void setupSalmonManager() {
        try {
            SalmonDriveManager.setVirtualDriveClass(AndroidDrive.class);
            if (SalmonDriveManager.getSequencer() != null)
                SalmonDriveManager.getSequencer().close();
            setupFileSequencer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupFileSequencer() throws Exception {
        String dirPath = getNoBackupFilesDir() + File.separator + SEQUENCER_DIR_NAME;
        String filePath = dirPath + File.separator + SEQUENCER_FILE_NAME;
        IRealFile dirFile = new JavaFile(dirPath);
        if (!dirFile.exists())
            dirFile.mkdir();
        IRealFile seqFile = new JavaFile(filePath);
        FileSequencer sequencer = new FileSequencer(seqFile, new AndroidSequenceParser());
        SalmonDriveManager.setSequencer(sequencer);
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuCompat.setGroupDividerEnabled(menu, true);
        menu.clear();

        menu.add(1, Action.OPEN_VAULT.ordinal(), 0, getResources().getString(R.string.OpenVault))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(1, Action.CREATE_VAULT.ordinal(), 0, getResources().getString(R.string.NewVault))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(1, Action.CLOSE_VAULT.ordinal(), 0, getResources().getString(R.string.CloseVault))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        if (fileCommander.isRunning()) {
            menu.add(2, Action.STOP.ordinal(), 0, getResources().getString(R.string.Stop))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }

        if (mode == Mode.Copy || mode == Mode.Move) {
            menu.add(3, Action.PASTE.ordinal(), 0, getResources().getString(R.string.Paste));
        }
        menu.add(3, Action.IMPORT.ordinal(), 0, getResources().getString(R.string.Import))
                .setIcon(android.R.drawable.ic_menu_add)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(3, Action.NEW_FOLDER.ordinal(), 0, getString(R.string.NewFolder))
                .setIcon(android.R.drawable.ic_input_add);

        if (adapter.getMode() == FileAdapter.Mode.MULTI_SELECT) {
            menu.add(3, Action.COPY.ordinal(), 0, getResources().getString(R.string.Copy));
            menu.add(3, Action.CUT.ordinal(), 0, getResources().getString(R.string.Cut));
            menu.add(3, Action.DELETE.ordinal(), 0, getResources().getString(R.string.Delete));
            menu.add(3, Action.EXPORT.ordinal(), 0, getResources().getString(R.string.Export));
            menu.add(3, Action.SELECT_ALL.ordinal(), 0, getResources().getString(R.string.SelectAll));
            menu.add(3, Action.UNSELECT_ALL.ordinal(), 0, getResources().getString(R.string.UnselectAll));
        }

        menu.add(4, Action.REFRESH.ordinal(), 0, getResources().getString(R.string.Refresh))
                .setIcon(android.R.drawable.ic_menu_rotate)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(4, Action.SORT.ordinal(), 0, getResources().getString(R.string.Sort))
                .setIcon(android.R.drawable.ic_menu_sort_alphabetically);
        menu.add(4, Action.SEARCH.ordinal(), 0, getResources().getString(R.string.Search))
                .setIcon(android.R.drawable.ic_menu_search);
        if (adapter.getMode() == FileAdapter.Mode.SINGLE_SELECT)
            menu.add(4, Action.MULTI_SELECT.ordinal(), 0, getString(R.string.MultiSelect))
                    .setIcon(android.R.drawable.ic_menu_agenda);
        else
            menu.add(4, Action.SINGLE_SELECT.ordinal(), 0, getString(R.string.SingleSelect))
                    .setIcon(android.R.drawable.ic_menu_agenda);


        if (SalmonDriveManager.getDrive() != null) {
            menu.add(5, Action.IMPORT_AUTH.ordinal(), 0, getResources().getString(R.string.ImportAuthFile))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(5, Action.EXPORT_AUTH.ordinal(), 0, getResources().getString(R.string.ExportAuthFile))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(5, Action.REVOKE_AUTH.ordinal(), 0, getResources().getString(R.string.RevokeAuth))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(5, Action.DISPLAY_AUTH_ID.ordinal(), 0, getResources().getString(R.string.DisplayAuthID))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }

        menu.add(6, Action.SETTINGS.ordinal(), 0, getResources().getString(R.string.Settings))
                .setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(6, Action.ABOUT.ordinal(), 0, getResources().getString(R.string.About))
                .setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(6, Action.EXIT.ordinal(), 0, getResources().getString(R.string.Exit))
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel);

        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (Action.values()[item.getItemId()]) {
            case OPEN_VAULT:
                onOpenVault();
                break;
            case CREATE_VAULT:
                onCreateVault();
                break;
            case CLOSE_VAULT:
                onCloseVault();
                break;

            case REFRESH:
                refresh();
                return true;
            case IMPORT:
                promptImportFiles();
                return true;
            case EXPORT:
                exportSelectedFiles();
                return true;
            case NEW_FOLDER:
                promptNewFolder();
                return true;
            case COPY:
                mode = Mode.Copy;
                copyFiles = adapter.getSelectedFiles().toArray(new SalmonFile[0]);
                showTaskRunning(true, false);
                showTaskMessage(copyFiles.length + " " + getResources().getString(R.string.ItemsSelectedForCopy));
                adapter.setMultiSelect(false);
                return true;
            case CUT:
                mode = Mode.Move;
                copyFiles = adapter.getSelectedFiles().toArray(new SalmonFile[0]);
                showTaskRunning(true, false);
                showTaskMessage(copyFiles.length + " " + getResources().getString(R.string.ItemsSelectedForMove));
                adapter.setMultiSelect(false);
                return true;
            case DELETE:
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
                fileCommander.cancel();
                return true;
            case SORT:
                promptSortFiles();
                break;

            case IMPORT_AUTH:
                onImportAuth();
                break;
            case EXPORT_AUTH:
                onExportAuth();
                break;
            case REVOKE_AUTH:
                onRevokeAuth();
                break;
            case DISPLAY_AUTH_ID:
                onDisplayAuthID();
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


    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        menu.setHeaderTitle(getString(R.string.Action));
        menu.add(0, Action.VIEW.ordinal(), 0, getString(R.string.View))
                .setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, Action.VIEW_AS_TEXT.ordinal(), 0, getString(R.string.ViewAsText))
                .setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, Action.VIEW_EXTERNAL.ordinal(), 0, getString(R.string.ViewExternal))
                .setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, Action.EDIT.ordinal(), 0, getString(R.string.EditExternal))
                .setIcon(android.R.drawable.ic_menu_send);
        menu.add(0, Action.SHARE.ordinal(), 0, getString(R.string.ShareExternal))
                .setIcon(android.R.drawable.ic_menu_send);

        menu.add(1, Action.COPY.ordinal(), 0, getString(R.string.Copy))
                .setIcon(android.R.drawable.ic_menu_delete);
        menu.add(1, Action.CUT.ordinal(), 0, getString(R.string.Cut))
                .setIcon(android.R.drawable.ic_menu_delete);
        menu.add(1, Action.DELETE.ordinal(), 0, getString(R.string.Delete))
                .setIcon(android.R.drawable.ic_menu_delete);
        menu.add(1, Action.RENAME.ordinal(), 0, getString(R.string.Rename))
                .setIcon(android.R.drawable.ic_menu_edit);
        menu.add(1, Action.EXPORT.ordinal(), 0, getString(R.string.Export))
                .setIcon(android.R.drawable.btn_minus);

        menu.add(2, Action.PROPERTIES.ordinal(), 0, getString(R.string.Properties))
                .setIcon(android.R.drawable.ic_dialog_info);
    }

    public boolean onContextItemSelected(MenuItem item) {
        int position = adapter.getPosition();
        SalmonFile ifile = fileItemList.get(position);
        switch (Action.values()[item.getItemId()]) {
            case VIEW:
                openFile(position);
                break;
            case VIEW_AS_TEXT:
                startTextViewer(position);
                break;
            case VIEW_EXTERNAL:
                openWith(ifile, Action.VIEW_EXTERNAL.ordinal());
                break;
            case EDIT:
                openWith(ifile, Action.EDIT.ordinal());
                break;
            case SHARE:
                openWith(ifile, Action.SHARE.ordinal());
                break;
            case EXPORT:
                exportFile(ifile);
                break;
            case COPY:
                mode = Mode.Copy;
                copyFiles = new SalmonFile[]{ifile};
                showTaskRunning(true, false);
                showTaskMessage(copyFiles.length + " " + getResources().getString(R.string.ItemsSelectedForCopy));
                break;
            case CUT:
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
                Collections.sort(fileItemList, Comparators.defaultComparator);
                break;
            case Name:
                Collections.sort(fileItemList, Comparators.filenameAscComparator);
                break;
            case NameDesc:
                Collections.sort(fileItemList, Comparators.filenameDescComparator);
                break;
            case Size:
                Collections.sort(fileItemList, Comparators.sizeAscComparator);
                break;
            case SizeDesc:
                Collections.sort(fileItemList, Comparators.sizeDescComparator);
                break;
            case Type:
                Collections.sort(fileItemList, Comparators.typeAscComparator);
                break;
            case TypeDesc:
                Collections.sort(fileItemList, Comparators.typeDescComparator);
                break;
            case Date:
                Collections.sort(fileItemList, Comparators.dateAscComparator);
                break;
            case DateDesc:
                Collections.sort(fileItemList, Comparators.dateDescComparator);
                break;
        }
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
        ActivityCommon.openFilesystem(this, false, true, null, REQUEST_IMPORT_FILES);
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
                    }
                }
        );
    }

    private void promptSortFiles() {
        List<String> sortTypes = new ArrayList<>();
        SortType[] values = SortType.values();
        sortTypes.add(values[0].name());
        for (int i=1; i<values.length; i++) {
            sortTypes.add((i%2==1?"↓":"↑") + " " + values[i-(i+1)%2].name());
        }
        ArrayAdapter<CharSequence> itemsAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_activated_1, sortTypes.toArray(new String[0]));
        ActivityCommon.promptSingleValue(this, itemsAdapter, getString(R.string.Sort),
                sortTypes.indexOf(sortType.toString()), (DialogInterface dialog, int which) -> {
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
                fileCommander.deleteFiles(files, (file) -> {
                    runOnUiThread(() -> {
                        int pos = fileItemList.indexOf(file);
                        fileItemList.remove(pos);
                        adapter.getSelectedFiles().remove(file);
                        adapter.notifyItemRemoved(pos);
                    });
                });
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
                fileCommander.copyFiles(files, dir, move, (fileInfo) -> {
                    runOnUiThread(() -> {
                        fileProgress.setProgress(fileInfo.fileProgress);
                        filesProgress.setProgress((int) (fileInfo.processedFiles * 100F / fileInfo.totalFiles));
                        String action = move ? " Moving: " : " Copying: ";
                        showTaskMessage((fileInfo.processedFiles + 1) + "/" + fileInfo.totalFiles + action + fileInfo.filename);
                    });
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
        exportFiles(adapter.getSelectedFiles().toArray(new SalmonFile[0]));
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
								runOnUiThread(() ->
                    			{
									Toast toast = Toast.makeText(this, "Could not rename file: " + exception.getMessage(), Toast.LENGTH_LONG);
								});
                            }
                            adapter.notifyItemChanged(position);
                        });
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    private void exportFile(SalmonFile ifile) {
        exportFiles(new SalmonFile[]{ifile});
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
        if (action == Action.VIEW_EXTERNAL.ordinal()) {
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
                    adapter.getSelectedFiles().remove(oldSalmonFile);
                    if (oldSalmonFile.exists())
                        oldSalmonFile.delete();
                    sortFiles(sortType);
                    adapter.notifyDataSetChanged();
                }
                Toast.makeText(this, getString(R.string.FileSavedInSalmonVault), Toast.LENGTH_LONG).show();
            });
            done.release(1);
        });
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        Uri uri = data.getData();

        if (requestCode == REQUEST_IMPORT_FILES) {
            IRealFile[] filesToImport;
            try {
                filesToImport = ActivityCommon.getFilesFromIntent(this, data);
                importFiles(filesToImport, currDir, SettingsActivity.getDeleteAfterImport(this), (SalmonFile[] importedFiles) ->
                {
                    runOnUiThread(() -> refresh());
                });
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, getResources().getString(R.string.CouldNotImportFiles), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_OPEN_VAULT_DIR) {
            try {
                ActivityCommon.setUriPermissions(this, data, uri);
                SettingsActivity.setVaultLocation(this, uri.toString());
                ActivityCommon.OpenVault(this, uri.toString());
                clear();
                setupRootDir();
            } catch (Exception e) {
                Toast.makeText(this, "Could not open vault: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }

        } else if (requestCode == REQUEST_CREATE_VAULT_DIR) {
            ActivityCommon.promptSetPassword(this, (String pass) ->
            {
                try {
                    ActivityCommon.CreateVault(this, uri.toString(), pass);
                    Toast.makeText(this, "Vault created", Toast.LENGTH_LONG).show();
                    rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
                    currDir = rootDir;
                    refresh();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Could not create vault: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

        } else if (requestCode == REQUEST_IMPORT_AUTH_FILE) {
            try {
                SalmonDriveManager.importAuthFile(uri.toString());
                Toast.makeText(this, "Device is now Authorized", Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                ex.printStackTrace();
                Toast.makeText(this, "Could Not Import Auth: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }

        } else if (requestCode == REQUEST_EXPORT_AUTH_FILE) {
            try {
                DocumentFile dir = DocumentFile.fromTreeUri(this, uri);
                String filename = SalmonDriveManager.getAppDriveConfigFilename();
                SalmonDriveManager.exportAuthFile(exportAuthID, dir.getUri().toString(), filename);
                Toast.makeText(this, "Auth File Exported", Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                ex.printStackTrace();
                Toast.makeText(this, "Could Not Export Auth: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    public void clear() {
        logout();
        rootDir = null;
        currDir = null;
        runOnUiThread(() -> {
            fileItemList.clear();
            adapter.resetCache(listView);
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
            });
        }
    }

    protected boolean openFile(int position) {
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
        try {
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
                startTextViewer(position);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Could not open: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

    private void startTextViewer(int position) {
        try {
            if (fileItemList.get(position).getSize() > 1 * 1024 * 1024) {
                Toast.makeText(this, "File too large", Toast.LENGTH_LONG).show();
                return;
            }
            startWebViewer(position);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startWebViewer(int position) {
        try {
            ArrayList<SalmonFile> salmonFiles = new ArrayList<>();
            SalmonFile file = fileItemList.get(position);
            String filename = file.getBaseName();

            int pos = 0;
            for (int i = 0; i < fileItemList.size(); i++) {
                try {
                    SalmonFile listFile = fileItemList.get(i);
                    String listFilename = listFile.getBaseName();
                    if (i != position &&
                            (FileUtils.isImage(filename) && FileUtils.isImage(listFilename))
                            || (FileUtils.isText(filename) && FileUtils.isText(listFilename))) {
                        salmonFiles.add(listFile);
                    }
                    if (i == position) {
                        salmonFiles.add(listFile);
                        pos = salmonFiles.size() - 1;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Intent intent = new Intent(this, WebViewerActivity.class);
            WebViewerActivity.setContentFiles(pos, salmonFiles.toArray(new SalmonFile[0]));
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Could not open viewer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    protected void onDestroy() {
        logout();
        Utils.removeFromRecents(this, true);
        adapter.stop();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        SalmonFile parent = currDir.getParent();
        if (adapter.getMode() == FileAdapter.Mode.MULTI_SELECT) {
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

    public void exportFiles(SalmonFile[] items) {

        executor.submit(() ->
        {
            for (SalmonFile file : items) {
                if (file.isDirectory()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Cannot Export Directories select files only", Toast.LENGTH_LONG).show();
                    });
                    return;
                }
            }
            showTaskRunning(true);
            boolean success = false;
            try {
                success = fileCommander.exportFiles(items,
                        (progress, file) -> {
                            runOnUiThread(() -> {
                                filesProgress.setProgress(progress);
                            });
                        }, (IRealFile[] realFiles) ->
                        {
                            runOnUiThread(() -> {
                                for(SalmonFile item : items) {
                                    int pos = fileItemList.indexOf(item);
                                    fileItemList.remove(pos);
                                    adapter.getSelectedFiles().remove(item);
                                    adapter.notifyItemRemoved(pos);
                                }
                            });
                        });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Could not export files: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
            if (fileCommander.isStopped())
                showTaskMessage(getString(R.string.ExportStopped));
            else if (!success)
                showTaskMessage(getString(R.string.ExportFailed));
            else showTaskMessage(getString(R.string.ExportComplete));
            runOnUiThread(() -> {
                fileProgress.setProgress(100);
                filesProgress.setProgress(100);
                ActivityCommon.promptDialog(this, getString(R.string.Export), getString(R.string.FilesExportedTo)
                                + ": " + SalmonDriveManager.getDrive().getExportDir().getAbsolutePath(),
                        getString(android.R.string.ok), null, null, null);
            });
            new Handler(Looper.getMainLooper()).postDelayed(() ->
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
                            runOnUiThread(() -> {
                                filesProgress.setProgress(progress);
                            });
                        }, OnFinished);
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Could not import files: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
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
                    showTaskRunning(false), 2000);
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
                    setPath(getString(R.string.Search) + " " + getString(R.string.Stopped) + ": " + value);
            });
        });
    }


    public void onImportAuth() {
        if (SalmonDriveManager.getDrive() == null) {
            Toast.makeText(this, "No Drive Loaded", Toast.LENGTH_LONG).show();
            return;
        }
        // TODO: filter by extension
        String filename = SalmonDriveManager.getAppDriveConfigFilename();
        String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(filename);
        ActivityCommon.openFilesystem(this, false, false, null, REQUEST_IMPORT_AUTH_FILE);
    }

    public void onExportAuth() {
        if (SalmonDriveManager.getDrive() == null) {
            Toast.makeText(this, "No Drive Loaded", Toast.LENGTH_LONG).show();
            return;
        }

        ActivityCommon.promptEdit(this, "Export Auth File",
                "Enter the Auth ID for the device you want to authorize", "", null,
                (targetAuthID, option) ->
                {
                    exportAuthID = targetAuthID;
                    String filename = SalmonDriveManager.getAppDriveConfigFilename();
                    String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(filename);
                    ActivityCommon.openFilesystem(this, true, false, null, REQUEST_EXPORT_AUTH_FILE);
                });
    }

    public void onRevokeAuth() {
        if (SalmonDriveManager.getDrive() == null) {
            Toast.makeText(this, "No Drive Loaded", Toast.LENGTH_LONG).show();
            return;
        }
        ActivityCommon.promptDialog(this, "Revoke Auth", "Revoke Auth for this drive? You will still be able to decrypt and view your files but you won't be able to import any more files in this drive.",
                "Ok", (d, e) ->
                {
                    try {
                        SalmonDriveManager.revokeSequences();
                        Toast.makeText(this, "Revoke Auth Successful", Toast.LENGTH_LONG).show();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        Toast.makeText(this, "Could Not Revoke Auth: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }, "Cancel", null);
    }


    public void onDisplayAuthID() {
        if (SalmonDriveManager.getDrive() == null) {
            Toast.makeText(this, "No Drive Loaded", Toast.LENGTH_LONG).show();
            return;
        }
        String driveID;
        try {
            driveID = SalmonDriveManager.getAuthID();
            ActivityCommon.promptEdit(this, "Salmon Auth ID", "", driveID, null, null);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.Error) + ": "
                    + getString(R.string.CouldNotGetAuthID) + ": "
                    + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    public void setPath(String value) {
        if (value.startsWith("/"))
            value = value.substring(1);
        pathText.setText("salmonfs://" + value);
    }

    public SalmonFile[] getSalmonFiles() {
        return salmonFiles;
    }

    private void onOpenVault() {
        ActivityCommon.openFilesystem(this, true, false, null, REQUEST_OPEN_VAULT_DIR);
    }

    public void onCreateVault() {
        ActivityCommon.openFilesystem(this, true, false, null, REQUEST_CREATE_VAULT_DIR);
    }

    public void onCloseVault() {
        logout();
        rootDir = null;
        currDir = null;
        runOnUiThread(() ->
        {
            pathText.setText("");
            fileItemList.clear();
            adapter.notifyDataSetChanged();
        });
    }

    public enum MediaType {
        AUDIO, VIDEO
    }

    public enum Action {
        BACK, REFRESH, IMPORT, VIEW, VIEW_AS_TEXT, VIEW_EXTERNAL, EDIT, SHARE, SAVE,
        EXPORT, DELETE, RENAME, UP, DOWN,
        MULTI_SELECT, SINGLE_SELECT, SELECT_ALL, UNSELECT_ALL,
        COPY, CUT, PASTE,
        NEW_FOLDER, SEARCH, STOP, PLAY, SORT,
        OPEN_VAULT, CREATE_VAULT, CLOSE_VAULT, CHANGE_PASSWORD,
        IMPORT_AUTH, EXPORT_AUTH, REVOKE_AUTH, DISPLAY_AUTH_ID,
        PROPERTIES, SETTINGS, ABOUT, EXIT
    }

    enum Mode {
        Browse, Search, Copy, Move
    }

    private enum SortType {
        Default, Name, NameDesc, Size, SizeDesc, Type, TypeDesc, Date, DateDesc
    }
}