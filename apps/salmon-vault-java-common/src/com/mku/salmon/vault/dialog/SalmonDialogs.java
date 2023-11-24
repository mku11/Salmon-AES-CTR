package com.mku.salmon.vault.dialog;
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
import com.mku.func.Consumer;
import com.mku.salmon.vault.config.SalmonConfig;
import com.mku.salmon.vault.model.SalmonSettings;
import com.mku.salmon.vault.model.SalmonVaultManager;
import com.mku.salmon.vault.prefs.SalmonPreferences;
import com.mku.salmon.vault.services.IFileDialogService;
import com.mku.salmon.vault.services.IFileService;
import com.mku.salmon.vault.services.ServiceLocator;
import com.mku.salmon.vault.utils.URLUtils;
import com.mku.salmonfs.SalmonAuthException;
import com.mku.salmonfs.SalmonDriveManager;
import com.mku.salmonfs.SalmonFile;
import com.mku.utils.SalmonFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SalmonDialogs {
    public static void promptPassword(Runnable onAuthenticationSucceded) {
        SalmonDialog.promptEdit("Vault", "Password", (password, option) ->
        {
            if (password == null)
                return;
            try {
                SalmonDriveManager.getDrive().authenticate(password);
                if (onAuthenticationSucceded != null)
                    onAuthenticationSucceded.run();
            } catch (SalmonAuthException ex) {
                SalmonDialog.promptDialog("Vault", "Wrong password");
            } catch (IOException e) {
                SalmonDialog.promptDialog("Vault", "Error: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, "", false, false, true, null);
    }

    public static void promptSetPassword(Consumer<String> onPasswordChanged) {
        SalmonDialog.promptEdit("Password", "Type new password", (password, option) ->
        {
            if (password != null) {
                SalmonDialog.promptEdit("Password", "Retype password", (npassword, nOption) ->
                {
                    if (!npassword.equals(password)) {
                        SalmonDialog.promptDialog("Vault", "Passwords do not match", "Cancel");
                    } else {
                        if (onPasswordChanged != null)
                            onPasswordChanged.accept(password);
                    }
                }, null, false, false, true, null);
            }
        }, "", false, false, true, null);
    }

    public static void promptChangePassword() {
        SalmonDialogs.promptSetPassword((pass) ->
        {
            try {
                SalmonDriveManager.getDrive().setPassword(pass);
                SalmonDialog.promptDialog("Password changed");
            } catch (Exception e) {
                SalmonDialog.promptDialog("Could not change password: " + e.getMessage());
            }
        });
    }

    public static void promptImportAuth() {
        if (SalmonDriveManager.getDrive() == null) {
            SalmonDialog.promptDialog("Error", "No Drive Loaded");
            return;
        }

        String filename = SalmonDriveManager.getDefaultAuthConfigFilename();
        String ext = SalmonFileUtils.getExtensionFromFileName(filename);
        HashMap<String, String> filter = new HashMap<>();
        filter.put("Salmon Auth Files", ext);
        ServiceLocator.getInstance().resolve(IFileDialogService.class).openFile("Import Auth File",
                filename, filter, SalmonSettings.getInstance().getVaultLocation(), (filePath) ->
                {
                    try {
                        SalmonDriveManager.importAuthFile((String) filePath);
                        SalmonDialog.promptDialog("Auth", "Device is now Authorized");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        SalmonDialog.promptDialog("Auth", "Could Not Import Auth: " + ex.getMessage());
                    }
                }, SalmonVaultManager.REQUEST_IMPORT_AUTH_FILE);
    }

    public static void promptExportAuth() {
        if (SalmonDriveManager.getDrive() == null) {
            SalmonDialog.promptDialog("Error", "No Drive Loaded");
            return;
        }
        SalmonDialog.promptEdit("Export Auth File",
                "Enter the Auth ID for the device you want to authorize",
                (targetAuthID, option) ->
                {
                    String filename = SalmonDriveManager.getDefaultAuthConfigFilename();
                    String ext = SalmonFileUtils.getExtensionFromFileName(filename);
                    HashMap<String, String> filter = new HashMap<>();
                    filter.put("Salmon Auth Files", ext);
                    ServiceLocator.getInstance().resolve(IFileDialogService.class).saveFile("Export Auth file",
                            filename, filter, SalmonSettings.getInstance().getVaultLocation(), (fileResult) ->
                            {
                                try {
                                    SalmonDriveManager.exportAuthFile(targetAuthID, ((String[]) fileResult)[0], ((String[]) fileResult)[1]);
                                    SalmonDialog.promptDialog("Auth", "Auth File Exported");
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    SalmonDialog.promptDialog("Auth", "Could Not Export Auth: " + ex.getMessage());
                                }
                            }, SalmonVaultManager.REQUEST_EXPORT_AUTH_FILE);
                }, "", false, false, false, null);
    }

    public static void promptRevokeAuth() {
        if (SalmonDriveManager.getDrive() == null) {
            SalmonDialog.promptDialog("Error", "No Drive Loaded");
            return;
        }
        SalmonDialog.promptDialog("Revoke Auth",
                "Revoke Auth for this drive? You will still be able to decrypt and view your files but you won't be able to import any more files in this drive.",
                "Ok",
                () ->
                {
                    try {
                        SalmonDriveManager.revokeAuthorization();
                        SalmonDialog.promptDialog("Action", "Revoke Auth Successful");
                    } catch (Exception e) {
                        e.printStackTrace();
                        SalmonDialog.promptDialog("Action", "Could Not Revoke Auth: " + e.getMessage());
                    }
                },
                "Cancel", null);
    }

    public static void onDisplayAuthID() {
        try {
            if (SalmonDriveManager.getDrive() == null || SalmonDriveManager.getDrive().getDriveID() == null) {
                SalmonDialog.promptDialog("Error", "No Drive Loaded");
                return;
            }
            String driveID = SalmonDriveManager.getAuthID();
            SalmonDialog.promptEdit("Auth", "Salmon Auth App ID",
                    null, driveID, false, true, false, null);
        } catch (Exception ex) {
            SalmonDialog.promptDialog("Error", ex.getMessage());
        }
    }

    public static void showProperties(SalmonFile item) {
        try {
            SalmonDialog.promptDialog("Properties", SalmonVaultManager.getInstance().getFileProperties(item));
        } catch (Exception exception) {
            SalmonDialog.promptDialog("Properties", "Could not get file properties: "
                    + exception.getMessage());
            exception.printStackTrace();
        }
    }

    public static void promptSequenceReset(Consumer<Boolean> resetSequencer) {
        SalmonDialog.promptDialog("Warning", "The nonce sequencer file seems to be tampered.\n" +
                        "This could be a sign of a malicious attack. " +
                        "The recommended action is to press Reset to de-authorize all drives.\n" +
                        "Otherwise only if you know what you're doing press Continue.",
                "Reset", () ->
                {
                    resetSequencer.accept(false);
                },
                "Continue", () ->
                {
                    resetSequencer.accept(true);
                });
    }

    public static void promptDelete() {
        SalmonDialog.promptDialog(
                "Delete", "Delete " + SalmonVaultManager.getInstance().getSelectedFiles().size() + " item(s)?",
                "Ok",
                () -> SalmonVaultManager.getInstance().deleteSelectedFiles(),
                "Cancel", null);
    }

    public static void promptExit() {
        SalmonDialog.promptDialog("Exit",
                "Exit App?",
                "Ok",
                () ->
                {
                    SalmonVaultManager.getInstance().closeVault();
                    System.exit(0);
                }, "Cancel", null);
    }

    public static void promptAnotherProcessRunning() {
        SalmonDialog.promptDialog("File Search", "Another process is running");
    }

    public static void promptSearch() {
        SalmonDialog.promptEdit("Search", "Keywords",
                (value, isChecked) ->
                {
                    SalmonVaultManager.getInstance().search(value, isChecked);
                }, "", false, false, false, "Any Term");
    }

    public static void promptAbout() {
        SalmonDialog.promptDialog("About", SalmonConfig.APP_NAME
                        + " v" + SalmonConfig.getVersion() + "\n" + SalmonConfig.ABOUT_TEXT,
                "Project Website", () -> {
                    try {
                        URLUtils.goToUrl(SalmonConfig.SourceCodeURL);
                    } catch (Exception ex) {
                        SalmonDialog.promptDialog("Error", "Could not open Url: "
                                + SalmonConfig.SourceCodeURL);
                    }
                }, "Ok", null);
    }

    public static void promptSelectRoot() {
        SalmonDialog.promptDialog("Vault",
                "Choose a location for your vault",
                "Ok",
                SalmonDialogs::promptOpenVault,
                "Cancel",
                null
        );
    }

    public static void promptCreateVault() {
        ServiceLocator.getInstance().resolve(IFileDialogService.class).pickFolder("Select the vault",
                SalmonSettings.getInstance().getVaultLocation(), (filePath) -> {
                    SalmonDialogs.promptSetPassword((String pass) ->
                    {
                        try {
                            SalmonVaultManager.getInstance().createVault((String) filePath, pass);
                            SalmonDialog.promptDialog("Action", "Vault created, you can start importing your files");
                        } catch (Exception e) {
                            SalmonDialog.promptDialog("Error", "Could not create vault: " + e.getMessage());
                        }
                    });
                },
                SalmonVaultManager.REQUEST_CREATE_VAULT_DIR);
    }

    public static void promptOpenVault() {
        ServiceLocator.getInstance().resolve(IFileDialogService.class).pickFolder("Select the vault",
                SalmonSettings.getInstance().getVaultLocation(), (filePath) ->
                        SalmonVaultManager.getInstance().openVault((String) filePath),
                SalmonVaultManager.REQUEST_OPEN_VAULT_DIR);
    }

    public static void promptImportFiles() {
        ServiceLocator.getInstance().resolve(IFileDialogService.class).openFiles("Select files to import",
                null, SalmonSettings.getInstance().getLastImportDir(), (obj) ->
                {
                    String[] files = (String[]) obj;
                    List<IRealFile> filesToImport = new ArrayList<>();
                    IFileService fileService = ServiceLocator.getInstance().resolve(IFileService.class);
                    for (String file : files) {
                        filesToImport.add(fileService.getFile(file, false));
                    }
                    SalmonSettings.getInstance().setLastImportDir(new File(files[0]).getParentFile().getAbsolutePath());
                    SalmonPreferences.savePrefs();
                    SalmonVaultManager.getInstance().importFiles(filesToImport.toArray(new IRealFile[0]),
                            SalmonVaultManager.getInstance().getCurrDir(), SalmonSettings.getInstance().isDeleteAfterImport(), (SalmonFile[] importedFiles) ->
                            {
                                SalmonVaultManager.getInstance().refresh();
                            });
                }, SalmonVaultManager.REQUEST_IMPORT_FILES);
    }

    public static void promptNewFolder() {
        SalmonDialog.promptEdit("Create Folder",
                "Folder Name",
                (folderName, isChecked) ->
                {
                    try {
                        SalmonVaultManager.getInstance().getCurrDir().createDirectory(folderName, null, null);
                        SalmonVaultManager.getInstance().refresh();
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        if (!SalmonVaultManager.getInstance().handleException(exception)) {
                            SalmonDialog.promptDialog("Error", "Could Not Create Folder: " + exception.getMessage());
                        }
                    }
                }, "New Folder", true, false, false, null);
    }

    public static void promptRenameFile(SalmonFile ifile) {
        String currentFilename = "";
        try {
            currentFilename = ifile.getBaseName();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            SalmonDialog.promptEdit("Rename",
                    "New filename",
                    (newFilename, isChecked) ->
                    {
                        if (newFilename == null)
                            return;
                        try {
                            SalmonVaultManager.getInstance().renameFile(ifile, newFilename);
                        } catch (Exception exception) {
                            exception.printStackTrace();
                            if (!SalmonVaultManager.getInstance().handleException(exception)) {
                                SalmonDialog.promptDialog("Error: " + exception.getMessage());
                            }
                        }
                        SalmonVaultManager.getInstance().updateListItem.accept(ifile);
                    }, currentFilename, true, false, false, null);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
