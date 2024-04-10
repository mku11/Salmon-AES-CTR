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

import { ServiceLocator } from "../services/service_locator.js";
import { IFileDialogService } from "../services/ifile_dialog_service.js";
import { SalmonSettings } from "../model/salmon_settings.js";
import { SalmonVaultManager } from "../model/salmon_vault_manager.js";
import { SalmonDrive } from "../../lib/salmon-fs/salmon/salmon_drive.js";
import { SalmonDialog } from "../../vault/dialog/salmon_dialog.js";
import { SalmonConfig } from "../../vault/config/salmon_config.js";
import { URLUtils } from "../../vault/utils/url_utils.js";
import { FileUtils } from "../../lib/salmon-fs/utils/file_utils.js";
import { IFileRemoteService } from "../../common/services/ifile_remote_service.js";

export class SalmonDialogs {
    static promptPassword(onSubmit) {
        SalmonDialog.promptEdit("Vault", "Password", async (password, option) => {
            if (onSubmit)
                onSubmit(password);
        }, "", false, false, true, null);
    }

    static promptSetPassword(onPasswordChanged) {
        SalmonDialog.promptEdit("Password", "Type new password", (password, option) => {
            if (password != null) {
                SalmonDialog.promptEdit("Password", "Retype password", (npassword, nOption) => {
                    if (npassword != password) {
                        SalmonDialog.promptDialog("Vault", "Passwords do not match", "Cancel");
                    } else {
                        if (onPasswordChanged != null)
                            onPasswordChanged(password);
                    }
                }, "", false, false, true, null);
            }
        }, "", false, false, true, null);
    }

    static promptChangePassword() {
        if(!SalmonDialogs.driveLoaded())
            return;
        SalmonDialogs.promptSetPassword(async (pass) => {
            try {
                await SalmonVaultManager.getInstance().getDrive().setPassword(pass);
                SalmonDialog.promptDialog("Vault", "Password changed");
            } catch (e) {
                SalmonDialog.promptDialog("Error", "Could not change password: " + e);
            }
        });
    }

    static promptImportAuth() {
        if(!SalmonDialogs.driveLoaded())
            return;
        let filename = SalmonDrive.getDefaultAuthConfigFilename();
        let ext = FileUtils.getExtensionFromFileName(filename);
        let filter = {};
        filter["Salmon Auth Files"] = ext;
        ServiceLocator.getInstance().resolve(IFileDialogService).openFile("Import Auth File",
            filename, filter, SalmonSettings.getInstance().getVaultLocation(), async (file) => {
                try {
                    await SalmonVaultManager.getInstance().getDrive().importAuthFile(file);
                    SalmonDialog.promptDialog("Auth", "Device is now Authorized");
                } catch (ex) {
                    console.error(ex);
                    SalmonDialog.promptDialog("Auth", "Could Not Import Auth: " + ex);
                }
            }, SalmonVaultManager.REQUEST_IMPORT_AUTH_FILE);
    }

    static promptExportAuth() {
        if(!SalmonDialogs.driveLoaded())
            return;
        SalmonDialog.promptEdit("Export Auth File",
            "Enter the Auth ID for the device you want to authorize",
            (targetAuthId, option) => {
                let filename = SalmonDrive.getDefaultAuthConfigFilename();
                let ext = FileUtils.getExtensionFromFileName(filename);
                let filter = {};
                filter["Salmon Auth Files"] = ext;
                ServiceLocator.getInstance().resolve(IFileDialogService).saveFile("Export Auth file",
                    filename, filter, SalmonSettings.getInstance().getVaultLocation(), async (fileResult) => {
                        try {
                            await SalmonVaultManager.getInstance().getDrive().exportAuthFile(targetAuthId, fileResult);
                            SalmonDialog.promptDialog("Auth", "Auth File Exported");
                        } catch (ex) {
                            console.error(ex);
                            SalmonDialog.promptDialog("Auth", "Could Not Export Auth: " + ex);
                        }
                    }, SalmonVaultManager.REQUEST_EXPORT_AUTH_FILE);
            }, "", false, false, false, null);
    }

    static promptRevokeAuth() {
        if(!SalmonDialogs.driveLoaded())
            return;
        SalmonDialog.promptDialog("Revoke Auth",
            "Revoke Auth for this drive? You will still be able to decrypt and view your files but you won't be able to import any more files in this drive.",
            "Ok",
            () => {
                try {
                    SalmonVaultManager.getInstance().getDrive().revokeAuthorization();
                    SalmonDialog.promptDialog("Action", "Revoke Auth Successful");
                } catch (e) {
                    console.error(e);
                    SalmonDialog.promptDialog("Action", "Could Not Revoke Auth: " + e);
                }
            },
            "Cancel", null);
    }

    static async onDisplayAuthId() {
        if(!SalmonDialogs.driveLoaded())
            return;

        try {
            let driveId = await SalmonVaultManager.getInstance().getDrive().getAuthId();
            SalmonDialog.promptEdit("Auth", "Salmon Auth App ID",
                null, driveId, false, true, false, null);
        } catch (ex) {
            SalmonDialog.promptDialog("Error", ex);
        }
    }

    static async showProperties(item) {
        try {
            SalmonDialog.promptDialog("Properties", await SalmonVaultManager.getInstance().getFileProperties(item));
        } catch (exception) {
            SalmonDialog.promptDialog("Properties", "Could not get file properties: "
                + exception);
            console.error(exception);
        }
    }

    static promptSequenceReset(resetSequencer) {
        SalmonDialog.promptDialog("Warning", "The nonce sequencer file seems to be tampered.\n" +
            "This could be a sign of a malicious attack. " +
            "The recommended action is to press Reset to de-authorize all drives.\n" +
            "Otherwise only if you know what you're doing press Continue.",
            "Reset", () => {
                resetSequencer(false);
            },
            "Continue", () => {
                resetSequencer(true);
            });
    }

    static promptDelete() {
        if(!SalmonDialogs.driveLoaded())
            return;
        SalmonDialog.promptDialog(
            "Delete", "Delete " + SalmonVaultManager.getInstance().getSelectedFiles().size + " item(s)?",
            "Ok",
            () => SalmonVaultManager.getInstance().deleteSelectedFiles(),
            "Cancel", null);
    }

    static promptExit() {
        SalmonDialog.promptDialog("Exit",
            "Exit App?",
            "Ok",
            () => {
                SalmonVaultManager.getInstance().closeVault();
                System.exit(0);
            }, "Cancel", null);
    }

    static promptAnotherProcessRunning() {
        SalmonDialog.promptDialog("File Search", "Another process is running");
    }

    static promptSearch() {
        if(!SalmonDialogs.driveLoaded())
            return;

        SalmonDialog.promptEdit("Search", "Keywords",
            async (value, isChecked) => {
                await SalmonVaultManager.getInstance().search(value, isChecked);
            }, "", false, false, false, "Any Term");
    }

    static promptAbout() {
        SalmonDialog.promptDialog("About", SalmonConfig.APP_NAME
            + " v" + SalmonConfig.getVersion() + "\n" + SalmonConfig.ABOUT_TEXT,
            "Project Website", () => {
                try {
                    URLUtils.goToUrl(SalmonConfig.SourceCodeURL);
                } catch (ex) {
                    SalmonDialog.promptDialog("Error", "Could not open Url: "
                        + SalmonConfig.SourceCodeURL + ex);
                }
            }, "Ok", null);
    }

    static promptCreateVault() {
        ServiceLocator.getInstance().resolve(IFileDialogService).pickFolder("Select the vault",
            SalmonSettings.getInstance().getVaultLocation(), (file) => {
                SalmonDialogs.promptSetPassword(async (pass) => {
                    try {
                        await SalmonVaultManager.getInstance().createVault(file, pass);
                        SalmonDialog.promptDialog("Action", "Vault created, you can start importing your files");
                    } catch (e) {
                        SalmonDialog.promptDialog("Error", "Could not create vault: " + e);
                    }
                });
            },
            SalmonVaultManager.REQUEST_CREATE_VAULT_DIR);
    }

    static promptOpenVault(msg) {
        SalmonDialog.promptDialog("Open Vault", msg,
            "Local", () => {
                this.promptOpenLocalVault();
            }, "Remote", () => {
                this.promptOpenRemoteVault();
            });
    }

    static promptOpenLocalVault() {
        ServiceLocator.getInstance().resolve(IFileDialogService).pickFolder("Select the vault",
            SalmonSettings.getInstance().getVaultLocation(),
            (dir) => {
                SalmonDialogs.promptPassword(async (password) => {
                    await SalmonVaultManager.getInstance().openVault(dir, password);
                });
            },
            SalmonVaultManager.REQUEST_OPEN_VAULT_DIR);
    }

    static promptOpenRemoteVault() {
        SalmonDialog.promptEdit("Open Remote Vault",
            "Type in the HTTP url for the remote vault",
            async (url, isChecked) => {
                let dir = ServiceLocator.getInstance().resolve(IFileRemoteService).getFile(url);
				SalmonDialogs.promptPassword(async (password) => {
					await SalmonVaultManager.getInstance().openVault(dir, password);
				});
            }, "");
    }

    static promptImportFiles() {
        if(!SalmonDialogs.driveLoaded())
            return;
        ServiceLocator.getInstance().resolve(IFileDialogService).openFiles("Select files to import",
            null, SalmonSettings.getInstance().getLastImportDir(), async (obj) => {
                let filesToImport = obj;
                if (filesToImport.length == 0)
                    return;
                SalmonSettings.getInstance().setLastImportDir("");
                SalmonVaultManager.getInstance().importFiles(filesToImport,
                    SalmonVaultManager.getInstance().getCurrDir(), SalmonSettings.getInstance().isDeleteAfterImport(), async (importedFiles) => {
                        await SalmonVaultManager.getInstance().refresh();
                    });
            }, SalmonVaultManager.REQUEST_IMPORT_FILES);
    }

    static promptNewFolder() {
        if(!SalmonDialogs.driveLoaded())
            return;

        SalmonDialog.promptEdit("Create Folder",
            "Folder Name",
            async (folderName, isChecked) => {
                try {
                    await SalmonVaultManager.getInstance().getCurrDir().createDirectory(folderName, null, null);
                    await SalmonVaultManager.getInstance().refresh();
                } catch (exception) {
                    console.error(exception);
                    if (!SalmonVaultManager.getInstance().handleException(exception)) {
                        SalmonDialog.promptDialog("Error", "Could Not Create Folder: " + exception);
                    }
                }
            }, "New Folder", true, false, false, null);
    }

    static async promptRenameFile(ifile) {
        let currentFilename = "";
        try {
            currentFilename = await ifile.getBaseName();
        } catch (ex) {
            console.error(ex);
        }

        try {
            SalmonDialog.promptEdit("Rename",
                "New filename",
                async (newFilename, isChecked) => {
                    if (newFilename == null)
                        return;
                    try {
                        await SalmonVaultManager.getInstance().renameFile(ifile, newFilename);
                    } catch (exception) {
                        console.error(exception);
                        if (!SalmonVaultManager.getInstance().handleException(exception)) {
                            SalmonDialog.promptDialog("Error", exception);
                        }
                    }
                    await SalmonVaultManager.getInstance().updateListItem(ifile);
                }, currentFilename, true, false, false, null);
        } catch (exception) {
            console.error(exception);
        }
    }

    static driveLoaded() {
        if (SalmonVaultManager.getInstance().getDrive() == null) {
            SalmonDialog.promptDialog("Error", "No Drive Loaded");
            return false;
        }
        return true;
    }
}
