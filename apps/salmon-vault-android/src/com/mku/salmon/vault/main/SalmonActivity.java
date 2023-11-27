package com.mku.salmon.vault.main;
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

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mku.android.file.AndroidDrive;
import com.mku.android.file.AndroidFile;
import com.mku.android.file.AndroidSharedFileObserver;
import com.mku.file.IRealFile;
import com.mku.func.Consumer;
import com.mku.salmon.vault.android.R;
import com.mku.salmon.vault.dialog.SalmonDialog;
import com.mku.salmon.vault.dialog.SalmonDialogs;
import com.mku.salmon.vault.model.SalmonVaultManager;
import com.mku.salmon.vault.model.android.SalmonAndroidVaultManager;
import com.mku.salmon.vault.services.AndroidBrowserService;
import com.mku.salmon.vault.services.AndroidFileDialogService;
import com.mku.salmon.vault.services.AndroidFileService;
import com.mku.salmon.vault.services.AndroidKeyboardService;
import com.mku.salmon.vault.services.AndroidSettingsService;
import com.mku.salmon.vault.services.IFileDialogService;
import com.mku.salmon.vault.services.IFileService;
import com.mku.salmon.vault.services.IKeyboardService;
import com.mku.salmon.vault.services.ISettingsService;
import com.mku.salmon.vault.services.IWebBrowserService;
import com.mku.salmon.vault.services.ServiceLocator;
import com.mku.salmon.vault.utils.WindowUtils;
import com.mku.salmonfs.SalmonAuthException;
import com.mku.salmonfs.SalmonDrive;
import com.mku.salmonfs.SalmonDriveManager;
import com.mku.salmonfs.SalmonFile;
import com.mku.salmonfs.SalmonFileComparators;
import com.mku.utils.SalmonFileUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

public class SalmonActivity extends AppCompatActivity {
    private static final String TAG = SalmonApplication.class.getSimpleName();

    private static final long MAX_FILE_SIZE_TO_SHARE = 50 * 1024 * 1024;
    private static final long MEDIUM_FILE_SIZE_TO_SHARE = 10 * 1024 * 1024;

    private List<SalmonFile> fileItemList = new ArrayList<>();

    private Semaphore done = new Semaphore(1);

    private TextView pathText;
    private RecyclerView listView;
    private FileAdapter adapter;
    private View progressLayout;
    private TextView statusText;
    private ProgressBar fileProgress;
    private ProgressBar filesProgress;
    private TextView fileProgressText;
    private TextView filesProgressText;

    private SortType sortType = SortType.Default;
    private SalmonVaultManager manager;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setupServices();
        setupWindow();
        setContentView(R.layout.main);
        setupControls();
        setupSalmonManager();
    }

    protected void setupServices() {
        ServiceLocator.getInstance().register(ISettingsService.class, new AndroidSettingsService());
        ServiceLocator.getInstance().register(IFileService.class, new AndroidFileService(this));
        ServiceLocator.getInstance().register(IFileDialogService.class, new AndroidFileDialogService(this));
        ServiceLocator.getInstance().register(IWebBrowserService.class, new AndroidBrowserService());
        ServiceLocator.getInstance().register(IKeyboardService.class, new AndroidKeyboardService(this));
    }

    private void setupWindow() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        WindowUtils.setUiActivity(this);
    }

    private void setupControls() {
        fileProgress = (ProgressBar) findViewById(R.id.fileProgress);
        filesProgress = (ProgressBar) findViewById(R.id.filesProgress);
        fileProgressText = (TextView) findViewById(R.id.fileProgressText);
        filesProgressText = (TextView) findViewById(R.id.filesProgressText);

        statusText = (TextView) findViewById(R.id.status);
        progressLayout = findViewById(R.id.progress_layout);
        progressLayout.setVisibility(View.GONE);
        pathText = (TextView) findViewById(R.id.path);
        pathText.setText("");
        listView = (RecyclerView) findViewById(R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
        registerForContextMenu(listView);
        adapter = createAdapter();
        adapter.setOnCacheCleared(this::clearRecyclerViewCache);
        listView.setAdapter(adapter);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayUseLogoEnabled(true);
        getSupportActionBar().setLogo(R.drawable.logo_48x48);
    }

    private void clearRecyclerViewCache() {
        listView.getRecycledViewPool().clear();
        listView.setRecycledViewPool(new RecyclerView.RecycledViewPool());
    }

    protected FileAdapter createAdapter() {
        return new FileAdapter(this, fileItemList, (Integer pos) ->
        {
            try {
                return openItem(pos);
            } catch (Exception e) {
                e.printStackTrace();
                SalmonDialog.promptDialog("Could not open item: " + e.getMessage());
            }
            return false;
        });
    }

    private void setupSalmonManager() {
        try {
            AndroidDrive.initialize(this.getApplicationContext());
            SalmonDriveManager.setVirtualDriveClass(AndroidDrive.class);

            manager = createVaultManager();
            manager.openListItem = this::openListItem;
            manager.observePropertyChanges(this::manager_PropertyChanged);
            manager.updateListItem = this::updateListItem;
            manager.onFileItemAdded = this::fileItemAdded;
            adapter.observePropertyChanges(this::Adapter_PropertyChanged);
            WindowUtils.runOnMainThread(() ->
            {
                manager.initialize();
            }, 1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateListItem(SalmonFile file) {
        int index = fileItemList.indexOf(file);
        if (index >= 0)
            adapter.notifyItemChanged(index);
    }

    private void manager_PropertyChanged(Object owner, String propertyName) {
        WindowUtils.runOnMainThread(() ->
        {
            if (propertyName == "FileItemList") {
                UpdateFileAdapter();

                adapter.selectAll(false);
                adapter.setMultiSelect(false);
            } else if (propertyName == "SelectedFiles") {
                if (manager.getSelectedFiles().size() == 0) {
                    adapter.selectAll(false);
                    adapter.setMultiSelect(false);
                }
            } else if (propertyName == "Status") {
                statusText.setText(manager.getStatus());

            } else if (propertyName == "IsJobRunning") {
                WindowUtils.runOnMainThread(() ->
                {
                    if (manager.getFileManagerMode() != SalmonVaultManager.Mode.Search) {
                        progressLayout.setVisibility(manager.isJobRunning() ? View.VISIBLE : View.GONE);
                    }
                    if (!manager.isJobRunning())
                        statusText.setText("");
                }, manager.isJobRunning() ? 0 : 1000);
            } else if (propertyName == "Path") pathText.setText(manager.getPath());
            else if (propertyName == "FileProgress") {
                fileProgress.setProgress((int) (manager.getFileProgress() * 100));
                fileProgressText.setText(fileProgress.getProgress() + " %");
            } else if (propertyName == "FilesProgress") {
                filesProgress.setProgress((int) (manager.getFilesProgress() * 100));
                filesProgressText.setText(filesProgress.getProgress() + " %");
            }
        });
    }

    private void Adapter_PropertyChanged(Object owner, String propertyName) {
        if (propertyName == "SelectedFiles") {
            manager.getSelectedFiles().clear();
            for (SalmonFile file : adapter.getSelectedFiles())
                manager.getSelectedFiles().add(file);
        }
    }

    private void UpdateFileAdapter() {
        if (manager.getFileItemList() == null) {
            fileItemList.clear();
            adapter.notifyDataSetChanged();
        } else {
            fileItemList.clear();
            fileItemList.addAll(manager.getFileItemList());
            adapter.notifyDataSetChanged();
        }
    }

    private void fileItemAdded(int position, SalmonFile file) {
        WindowUtils.runOnMainThread(() ->
        {
            fileItemList.add(position, file);
            adapter.notifyItemInserted(position);
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuCompat.setGroupDividerEnabled(menu, true);
        menu.clear();

        menu.add(1, ActionType.OPEN_VAULT.ordinal(), 0, getResources().getString(R.string.OpenVault))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(1, ActionType.CREATE_VAULT.ordinal(), 0, getResources().getString(R.string.NewVault))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(1, ActionType.CLOSE_VAULT.ordinal(), 0, getResources().getString(R.string.CloseVault))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(1, ActionType.CHANGE_PASSWORD.ordinal(), 0, getResources().getString(R.string.ChangePasswordTitle))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        if (manager.isJobRunning()) {
            menu.add(2, ActionType.STOP.ordinal(), 0, getResources().getString(R.string.Stop))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }

        if (manager.getFileManagerMode() == SalmonVaultManager.Mode.Copy || manager.getFileManagerMode() == SalmonVaultManager.Mode.Move) {
            menu.add(3, ActionType.PASTE.ordinal(), 0, getResources().getString(R.string.Paste));
        }
        menu.add(3, ActionType.IMPORT.ordinal(), 0, getResources().getString(R.string.ImportFiles))
                .setIcon(android.R.drawable.ic_input_add)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(3, ActionType.NEW_FOLDER.ordinal(), 0, getString(R.string.NewFolder))
                .setIcon(android.R.drawable.ic_input_add);

        if (adapter.getMode() == FileAdapter.Mode.MULTI_SELECT) {
            menu.add(3, ActionType.COPY.ordinal(), 0, getResources().getString(R.string.Copy));
            menu.add(3, ActionType.CUT.ordinal(), 0, getResources().getString(R.string.Cut));
            menu.add(3, ActionType.DELETE.ordinal(), 0, getResources().getString(R.string.Delete));
            menu.add(3, ActionType.EXPORT.ordinal(), 0, getResources().getString(R.string.ExportFiles));
            menu.add(3, ActionType.EXPORT_AND_DELETE.ordinal(), 0, getResources().getString(R.string.ExportAndDeleteFiles));
        }

        menu.add(4, ActionType.REFRESH.ordinal(), 0, getResources().getString(R.string.Refresh))
                .setIcon(android.R.drawable.ic_menu_rotate)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(4, ActionType.SORT.ordinal(), 0, getResources().getString(R.string.Sort))
                .setIcon(android.R.drawable.ic_menu_sort_alphabetically);
        menu.add(4, ActionType.SEARCH.ordinal(), 0, getResources().getString(R.string.Search))
                .setIcon(android.R.drawable.ic_menu_search);

        if (adapter.getMode() == FileAdapter.Mode.SINGLE_SELECT) {
            menu.add(4, ActionType.MULTI_SELECT.ordinal(), 0, getString(R.string.MultiSelect))
                    .setIcon(android.R.drawable.ic_menu_agenda);
        } else {
            menu.add(4, ActionType.SELECT_ALL.ordinal(), 0, getString(R.string.SelectAll))
                    .setIcon(android.R.drawable.ic_menu_agenda);
            menu.add(4, ActionType.UNSELECT_ALL.ordinal(), 0, getString(R.string.UnselectAll))
                    .setIcon(android.R.drawable.ic_menu_agenda);
            menu.add(4, ActionType.SINGLE_SELECT.ordinal(), 0, getString(R.string.SingleSelect))
                    .setIcon(android.R.drawable.ic_menu_agenda);
        }

        if (SalmonDriveManager.getDrive() != null) {
            menu.add(5, ActionType.IMPORT_AUTH.ordinal(), 0, getResources().getString(R.string.ImportAuthFile))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(5, ActionType.EXPORT_AUTH.ordinal(), 0, getResources().getString(R.string.ExportAuthFile))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(5, ActionType.REVOKE_AUTH.ordinal(), 0, getResources().getString(R.string.RevokeAuth))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(5, ActionType.DISPLAY_AUTH_ID.ordinal(), 0, getResources().getString(R.string.DisplayAuthID))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }

        menu.add(6, ActionType.SETTINGS.ordinal(), 0, getResources().getString(R.string.Settings))
                .setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(6, ActionType.ABOUT.ordinal(), 0, getResources().getString(R.string.About))
                .setIcon(android.R.drawable.ic_menu_info_details);
        menu.add(6, ActionType.EXIT.ordinal(), 0, getResources().getString(R.string.Exit))
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (ActionType.values()[item.getItemId()]) {
            case OPEN_VAULT:
                SalmonDialogs.promptOpenVault();
                break;
            case CREATE_VAULT:
                SalmonDialogs.promptCreateVault();
                break;
            case CLOSE_VAULT:
                manager.closeVault();
                break;
            case CHANGE_PASSWORD:
                SalmonDialogs.promptChangePassword();
                break;

            case REFRESH:
                manager.refresh();
                return true;
            case IMPORT:
                SalmonDialogs.promptImportFiles();
                return true;
            case EXPORT:
                exportSelectedFiles(false);
                return true;
            case EXPORT_AND_DELETE:
                exportSelectedFiles(true);
                return true;
            case NEW_FOLDER:
                SalmonDialogs.promptNewFolder();
                return true;
            case COPY:
                manager.copySelectedFiles();
                adapter.setMultiSelect(false, false);
                return true;
            case CUT:
                manager.cutSelectedFiles();
                adapter.setMultiSelect(false, false);
                return true;
            case DELETE:
                SalmonDialogs.promptDelete();
                return true;
            case PASTE:
                manager.pasteSelected();
                return true;
            case SELECT_ALL:
                selectAll(true);
                return true;
            case UNSELECT_ALL:
                selectAll(false);
                return true;
            case SEARCH:
                SalmonDialogs.promptSearch();
                return true;
            case MULTI_SELECT:
                adapter.setMultiSelect(true);
                return true;
            case SINGLE_SELECT:
                adapter.setMultiSelect(false);
                return true;
            case STOP:
                manager.stopOperation();
                return true;
            case SORT:
                PromptSortFiles();
                break;

            case IMPORT_AUTH:
                SalmonDialogs.promptImportAuth();
                break;
            case EXPORT_AUTH:
                SalmonDialogs.promptExportAuth();
                break;
            case REVOKE_AUTH:
                SalmonDialogs.promptRevokeAuth();
                break;
            case DISPLAY_AUTH_ID:
                SalmonDialogs.onDisplayAuthID();
                break;

            case SETTINGS:
                StartSettings();
                return true;
            case ABOUT:
                SalmonDialogs.promptAbout();
                return true;
            case EXIT:
                Exit();
                return true;
        }
        super.onOptionsItemSelected(item);
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        menu.setHeaderTitle(getString(R.string.Action));
        menu.add(0, ActionType.VIEW.ordinal(), 0, getString(R.string.View))
                .setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, ActionType.VIEW_AS_TEXT.ordinal(), 0, getString(R.string.ViewAsText))
                .setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, ActionType.VIEW_EXTERNAL.ordinal(), 0, getString(R.string.ViewExternal))
                .setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, ActionType.EDIT.ordinal(), 0, getString(R.string.EditExternal))
                .setIcon(android.R.drawable.ic_menu_send);
        menu.add(0, ActionType.SHARE.ordinal(), 0, getString(R.string.ShareExternal))
                .setIcon(android.R.drawable.ic_menu_send);

        menu.add(1, ActionType.COPY.ordinal(), 0, getString(R.string.Copy))
                .setIcon(android.R.drawable.ic_menu_delete);
        menu.add(1, ActionType.CUT.ordinal(), 0, getString(R.string.Cut))
                .setIcon(android.R.drawable.ic_menu_delete);
        menu.add(1, ActionType.DELETE.ordinal(), 0, getString(R.string.Delete))
                .setIcon(android.R.drawable.ic_menu_delete);
        menu.add(1, ActionType.RENAME.ordinal(), 0, getString(R.string.Rename))
                .setIcon(android.R.drawable.ic_menu_edit);
        menu.add(1, ActionType.EXPORT.ordinal(), 0, getString(R.string.ExportFiles))
                .setIcon(android.R.drawable.btn_minus);
        menu.add(1, ActionType.EXPORT_AND_DELETE.ordinal(), 0, getString(R.string.ExportAndDeleteFiles))
                .setIcon(android.R.drawable.btn_minus);

        menu.add(2, ActionType.PROPERTIES.ordinal(), 0, getString(R.string.Properties))
                .setIcon(android.R.drawable.ic_dialog_info);
    }

    @Override
    public boolean
    onContextItemSelected(MenuItem item) {
        int position = adapter.getPosition();
        SalmonFile ifile = fileItemList.get(position);
        manager.getSelectedFiles().clear();
        manager.getSelectedFiles().add(ifile);

        switch (ActionType.values()[item.getItemId()]) {
            case VIEW:
                openItem(position);
                break;
            case VIEW_AS_TEXT:
                startTextViewer(position);
                break;
            case VIEW_EXTERNAL:
                openWith(ifile, ActionType.VIEW_EXTERNAL.ordinal());
                break;
            case EDIT:
                openWith(ifile, ActionType.EDIT.ordinal());
                break;
            case SHARE:
                openWith(ifile, ActionType.SHARE.ordinal());
                break;
            case EXPORT:
                exportSelectedFiles(false);
                break;
            case EXPORT_AND_DELETE:
                exportSelectedFiles(true);
                break;
            case COPY:
                manager.copySelectedFiles();
                adapter.setMultiSelect(false, false);
                break;
            case CUT:
                manager.cutSelectedFiles();
                adapter.setMultiSelect(false, false);
                break;
            case DELETE:
                SalmonDialogs.promptDelete();
                break;
            case RENAME:
                SalmonDialogs.promptRenameFile(ifile);
                break;
            case PROPERTIES:
                SalmonDialogs.showProperties(ifile);
                break;
        }
        return true;
    }

    private void exportSelectedFiles(boolean deleteSource) {
        try {
            manager.exportSelectedFiles(deleteSource);
        } catch (SalmonAuthException e) {
            SalmonDialog.promptDialog("Could not export file(s): " + e.getMessage());
        }
    }

    private boolean openItem(int position) {
        try {
            return manager.openItem(fileItemList.get(position));
        } catch (Exception e) {
            SalmonDialog.promptDialog("Could not open item: " + e.getMessage());
        }
        return true;
    }

    private void selectAll(boolean value) {
        adapter.selectAll(value);
    }

    public void showTaskMessage(String msg) {
        runOnUiThread(() -> statusText.setText(msg == null ? "" : msg));
    }

    private void SortFiles(SortType sortType) {
        this.sortType = sortType;
        switch (sortType) {
            case Default:
                Collections.sort(fileItemList, SalmonFileComparators.getDefaultComparator());
                break;
            case Name:
                Collections.sort(fileItemList, SalmonFileComparators.getFilenameAscComparator());
                break;
            case NameDesc:
                Collections.sort(fileItemList, SalmonFileComparators.getFilenameDescComparator());
                break;
            case Size:
                Collections.sort(fileItemList, SalmonFileComparators.getSizeAscComparator());
                break;
            case SizeDesc:
                Collections.sort(fileItemList, SalmonFileComparators.getSizeDescComparator());
                break;
            case Type:
                Collections.sort(fileItemList, SalmonFileComparators.getTypeAscComparator());
                break;
            case TypeDesc:
                Collections.sort(fileItemList, SalmonFileComparators.getTypeDescComparator());
                break;
            case Date:
                Collections.sort(fileItemList, SalmonFileComparators.getDateAscComparator());
                break;
            case DateDesc:
                Collections.sort(fileItemList, SalmonFileComparators.getDateDescComparator());
                break;
        }
    }

    private void PromptSortFiles() {
        List<String> sortTypes = new ArrayList<String>();
        SortType[] values = SortType.values();
        sortTypes.add(values[0].toString());
        for (int i = 1; i < values.length; i++) {
            sortTypes.add((i % 2 == 1 ? "↓" : "↑") + " " + values[i - (i + 1) % 2].toString());
        }

        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_activated_1, sortTypes.toArray(new String[0]));
        SalmonDialog.promptSingleValue(itemsAdapter, getString(R.string.Sort), -1,
                (AlertDialog dialog, Integer which) ->
                {
                    SortFiles(values[which]);
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                }
        );
    }

    private void Exit() {
        finish();
    }

    private void StartSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void openWith(SalmonFile salmonFile, int action) {
        try {
            if (salmonFile.getSize() > MAX_FILE_SIZE_TO_SHARE) {
                Toast toast = Toast.makeText(this, getString(R.string.FileSizeTooLarge), Toast.LENGTH_LONG);
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
                    ExternalAppChooser.chooseApp(this, salmonFile, action, this::reimportSharedFile);
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }).start();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void reimportSharedFile(android.net.Uri uri, AndroidSharedFileObserver fileObserver) {
        try {
            done.acquire(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            if (SalmonDriveManager.getDrive().getVirtualRoot() == null || !SalmonDriveManager.getDrive().isAuthenticated())
                return;
        } catch (SalmonAuthException e) {
            SalmonDialog.promptDialog("Could not reimport shared file: " + e.getMessage());
            return;
        }
        DocumentFile docFile = DocumentFile.fromSingleUri(SalmonApplication.getInstance().getApplicationContext(), uri);
        IRealFile realFile = new AndroidFile(docFile, this);

        SalmonFile oldSalmonFile = fileObserver.getSalmonFile();
        SalmonFile parentDir = oldSalmonFile.getParent();

        manager.importFiles(new IRealFile[]{realFile}, parentDir, false, (SalmonFile[]
                                                                                  importedSalmonFiles) ->
        {
            try {
                if (!importedSalmonFiles[0].exists())
                    return;
                // in case the list is meanwhile refreshed

                SalmonFile oldFile = null;
                for (SalmonFile file : fileItemList) {
                    if (file.getRealFile().getBaseName().equals(oldSalmonFile.getRealFile().getBaseName())) {
                        oldFile = file;
                    }
                }
                if (oldFile == null)
                    return;
                if (oldFile.exists())
                    oldFile.delete();
                if (oldFile.exists())
                    return;
                importedSalmonFiles[0].rename(oldSalmonFile.getBaseName());

                fileObserver.setSalmonFile(importedSalmonFiles[0]);
                runOnUiThread(() ->
                {
                    int index = fileItemList.indexOf(oldSalmonFile);
                    if (index < 0)
                        return;
                    fileItemList.remove(oldSalmonFile);
                    fileItemList.add(index, importedSalmonFiles[0]);

                    manager.getFileItemList().remove(oldSalmonFile);
                    manager.getFileItemList().add(index, importedSalmonFiles[0]);

                    adapter.notifyItemChanged(index);

                    Toast.makeText(this, getString(R.string.FileSavedInSalmonVault), Toast.LENGTH_LONG).show();
                });
                done.release(1);
            } catch (Exception ex) {
                ex.printStackTrace();
                SalmonDialog.promptDialog("Could not reimport shared file: " + ex.getMessage());
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        android.net.Uri uri = data.getData();
        if (requestCode == SalmonVaultManager.REQUEST_OPEN_VAULT_DIR) {
            ActivityCommon.setUriPermissions(data, uri);
            IRealFile file = ServiceLocator.getInstance().resolve(IFileService.class).getFile(uri.toString(), true);
            Consumer<Object> callback = ServiceLocator.getInstance().resolve(IFileDialogService.class).getCallback(requestCode);
            callback.accept(file.getPath());
        } else if (requestCode == SalmonVaultManager.REQUEST_CREATE_VAULT_DIR) {
            ActivityCommon.setUriPermissions(data, uri);
            IRealFile file = ServiceLocator.getInstance().resolve(IFileService.class).getFile(uri.toString(), true);
            Consumer<Object> callback = ServiceLocator.getInstance().resolve(IFileDialogService.class).getCallback(requestCode);
            callback.accept(file.getPath());
        } else if (requestCode == SalmonVaultManager.REQUEST_IMPORT_FILES) {
            String[] filesToImport = ActivityCommon.getFilesFromIntent(this, data);
            Consumer<Object> callback = ServiceLocator.getInstance().resolve(IFileDialogService.class).getCallback(requestCode);
            callback.accept(filesToImport);
        } else if (requestCode == SalmonVaultManager.REQUEST_IMPORT_AUTH_FILE) {
            String[] files = ActivityCommon.getFilesFromIntent(this, data);
            String file = files != null ? files[0] : null;
            if (file == null)
                return;
            Consumer<Object> callback = ServiceLocator.getInstance().resolve(IFileDialogService.class).getCallback(requestCode);
            callback.accept(file);
        } else if (requestCode == SalmonVaultManager.REQUEST_EXPORT_AUTH_FILE) {
            String[] dirs = ActivityCommon.getFilesFromIntent(this, data);
            String dir = dirs != null ? dirs[0] : null;
            if (dir == null)
                return;
            Consumer<Object> callback = ServiceLocator.getInstance().resolve(IFileDialogService.class).getCallback(requestCode);
            callback.accept(new String[]{dir, SalmonDrive.AUTH_CONFIG_FILENAME});
        }
    }

    public boolean openListItem(SalmonFile file) {
        try {
            if (SalmonFileUtils.isVideo(file.getBaseName()) || SalmonFileUtils.isAudio(file.getBaseName())) {
                StartMediaPlayer(fileItemList.indexOf(file));
                return true;
            } else if (SalmonFileUtils.isImage(file.getBaseName())) {
                StartWebViewer(fileItemList.indexOf(file));
                return true;
            } else if (SalmonFileUtils.isText(file.getBaseName())) {
                StartWebViewer(fileItemList.indexOf(file));
                return true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            SalmonDialog.promptDialog("Error", "Could not open: " + ex.getMessage());
        }
        return false;
    }

    private void Logout() {
        try {
            SalmonDriveManager.getDrive().close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void StartMediaPlayer(int position) {
        List<SalmonFile> salmonFiles = new ArrayList<SalmonFile>();
        int pos = 0;
        int i = 0;
        for (SalmonFile file : fileItemList) {
            String filename;
            try {
                filename = file.getBaseName();
                if (SalmonFileUtils.isVideo(filename) || SalmonFileUtils.isAudio(filename)) {
                    salmonFiles.add(file);
                }
                if (i == position)
                    pos = salmonFiles.size() - 1;
            } catch (Exception e) {
                e.printStackTrace();
            }
            i++;
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
            StartWebViewer(position);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void StartWebViewer(int position) {
        try {
            List<SalmonFile> salmonFiles = new ArrayList<>();
            SalmonFile file = fileItemList.get(position);
            String filename = file.getBaseName();

            int pos = 0;
            int i = 0;
            for (SalmonFile listFile : fileItemList) {
                try {
                    String listFilename = listFile.getBaseName();
                    if (i != position &&
                            ((SalmonFileUtils.isImage(filename) && SalmonFileUtils.isImage(listFilename))
                            || (SalmonFileUtils.isText(filename) && SalmonFileUtils.isText(listFilename)))) {
                        salmonFiles.add(listFile);
                    }
                    if (i == position) {
                        salmonFiles.add(listFile);
                        pos = salmonFiles.size() - 1;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                i++;
            }
            Intent intent = new Intent(this, WebViewerActivity.class);
            SalmonFile selectedFile = fileItemList.get(position);
            WebViewerActivity.setContentFiles(pos, salmonFiles.toArray(new SalmonFile[0]));
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Could not open viewer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        Logout();
        WindowUtils.removeFromRecents(this, true);
        adapter.stop();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (adapter.getMode() == FileAdapter.Mode.MULTI_SELECT) {
            adapter.setMultiSelect(false);
            adapter.selectAll(false);
        } else
            manager.goBack();
    }

    public enum SortType {
        Default, Name, NameDesc, Size, SizeDesc, Type, TypeDesc, Date, DateDesc
    }

    protected SalmonVaultManager createVaultManager() {
        return SalmonAndroidVaultManager.getInstance();
    }
}