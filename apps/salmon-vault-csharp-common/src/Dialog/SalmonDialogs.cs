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

using Mku.File;
using Mku.SalmonFS;
using Mku.Utils;
using Salmon.Vault.Config;
using Salmon.Vault.Extensions;
using Salmon.Vault.Model;
using Salmon.Vault.Services;
using Salmon.Vault.Settings;
using Salmon.Vault.Utils;
using System;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using Action = System.Action;

namespace Salmon.Vault.Dialog;

public class SalmonDialogs
{
    public static void PromptPassword(Action OnAuthenticationSucceded)
    {
        SalmonDialog.PromptEdit("Vault", "Password", (password, option) =>
        {
            if (password == null)
                return;
            try
            {
                SalmonDriveManager.Drive.Authenticate(password);
                if (OnAuthenticationSucceded != null)
                    OnAuthenticationSucceded();
            }
            catch (SalmonAuthException)
            {
                SalmonDialog.PromptDialog("Vault", "Wrong password");
            }
            catch (Exception e)
            {
                SalmonDialog.PromptDialog("Vault", "Error: " + e.Message);
            }
        }, isPassword: true);
    }

    public static void PromptSetPassword(Action<string> OnPasswordChanged)
    {
        SalmonDialog.PromptEdit("Password", "Type new password", (password, option) =>
        {
            if (password != null)
            {
                SalmonDialog.PromptEdit("Password", "Retype password", (npassword, option) =>
                {
                    if (!npassword.Equals(password))
                    {
                        SalmonDialog.PromptDialog("Vault", "Passwords do not match", "Cancel");
                    }
                    else
                    {
                        if (OnPasswordChanged != null)
                            OnPasswordChanged(password);
                    }
                }, isPassword: true);
            }
        });
    }

    public static void PromptChangePassword()
    {
        SalmonDialogs.PromptSetPassword((pass) =>
        {
            try
            {
                SalmonDriveManager.Drive.SetPassword(pass);
                SalmonDialog.PromptDialog("Password changed");
            }
            catch (Exception e)
            {
                SalmonDialog.PromptDialog("Could not change password: " + e.Message);
            }
        });
    }

    public static void PromptImportAuth()
    {
        if (SalmonDriveManager.Drive == null)
        {
            SalmonDialog.PromptDialog("Error", "No Drive Loaded");
            return;
        }

        string filename = SalmonDriveManager.GetDefaultAuthConfigFilename();
        string ext = SalmonFileUtils.GetExtensionFromFileName(filename);
        Dictionary<string, string> filter = new Dictionary<string, string>();
        filter["Salmon Auth Files"] = ext;
        ServiceLocator.GetInstance().Resolve<IFileDialogService>().OpenFile("Import Auth File",
            filename, filter, SalmonSettings.GetInstance().VaultLocation, (filePath) =>
        {
            try
            {
                SalmonDriveManager.ImportAuthFile((string)filePath);
                SalmonDialog.PromptDialog("Auth", "Device is now Authorized");
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
                SalmonDialog.PromptDialog("Auth", "Could Not Import Auth: " + ex.Message);
            }
        }, SalmonVaultManager.REQUEST_IMPORT_AUTH_FILE);
    }

    public static void PromptExportAuth()
    {
        if (SalmonDriveManager.Drive == null)
        {
            SalmonDialog.PromptDialog("Error", "No Drive Loaded");
            return;
        }
        SalmonDialog.PromptEdit("Export Auth File",
            "Enter the Auth ID for the device you want to authorize",
            (targetAuthID, option) =>
            {
                string filename = SalmonDriveManager.GetDefaultAuthConfigFilename();
                string ext = SalmonFileUtils.GetExtensionFromFileName(filename);
                Dictionary<string, string> filter = new Dictionary<string, string>();
                filter["Salmon Auth Files"] = ext;
                ServiceLocator.GetInstance().Resolve<IFileDialogService>().SaveFile("Export Auth file",
                    filename, filter, SalmonSettings.GetInstance().VaultLocation, (fileResult) =>
                    {
                        try
                        {
                            SalmonDriveManager.ExportAuthFile(targetAuthID, ((string[])fileResult)[0], ((string[])fileResult)[1]);
                            SalmonDialog.PromptDialog("Auth", "Auth File Exported");
                        }
                        catch (Exception ex)
                        {
                            Console.Error.WriteLine(ex);
                            SalmonDialog.PromptDialog("Auth", "Could Not Export Auth: " + ex.Message);
                        }
                    }, SalmonVaultManager.REQUEST_EXPORT_AUTH_FILE);
            });
    }

    public static void PromptRevokeAuth()
    {
        if (SalmonDriveManager.Drive == null)
        {
            SalmonDialog.PromptDialog("Error", "No Drive Loaded");
            return;
        }
        SalmonDialog.PromptDialog("Revoke Auth",
            "Revoke Auth for this drive? You will still be able to decrypt and view your files but you won't be able to import any more files in this drive.",
            "Ok",
            () =>
            {
                try
                {
                    SalmonDriveManager.RevokeAuthorization();
                    SalmonDialog.PromptDialog("Action", "Revoke Auth Successful");
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                    SalmonDialog.PromptDialog("Action", "Could Not Revoke Auth: " + e.Message);
                }
            },
            "Cancel");
    }

    public static void OnDisplayAuthID()
    {
        try
        {
            if (SalmonDriveManager.Drive == null || SalmonDriveManager.Drive.DriveID == null)
            {
                SalmonDialog.PromptDialog("Error", "No Drive Loaded");
                return;
            }
            string driveID = SalmonDriveManager.GetAuthID();
            SalmonDialog.PromptEdit("Auth", "Salmon Auth App ID", null, driveID, false, true);
        }
        catch (Exception ex)
        {
            SalmonDialog.PromptDialog("Error", ex.Message);
        }
    }

    public static void ShowProperties(SalmonFile item)
    {
        try
        {
            SalmonDialog.PromptDialog("Properties", SalmonVaultManager.Instance.GetFileProperties(item), "Ok");
        }
        catch (Exception exception)
        {
            SalmonDialog.PromptDialog("Properties", "Could not get file properties: " + exception.Message);
            Console.Error.WriteLine(exception);
        }
    }

    private static string GetFileProperties(SalmonFile item)
    {
        return "Name: " + item.BaseName + "\n" +
            "Path: " + item.Path + "\n" +
            (!item.IsDirectory ? ("Size: " + ByteUtils.GetBytes(item.Size, 2)
                + " (" + item.Size + " bytes)") : "Items: " + item.ListFiles().Length) + "\n" +
            "EncryptedName: " + item.RealFile.BaseName + "\n" +
            "EncryptedPath: " + item.RealFile.AbsolutePath + "\n" +
            (!item.IsDirectory ? "EncryptedSize: " + ByteUtils.GetBytes(item.RealFile.Length, 2)
                + " (" + item.RealFile.Length + " bytes)" : "") + "\n";
    }

    internal static void PromptSequenceReset(Action<bool> ResetSequencer)
    {

        SalmonDialog.PromptDialog("Warning", "The nonce sequencer file seems to be tampered.\n" +
            "This could be a sign of a malicious attack. The recommended action is to press Reset to de-authorize all drives.\n" +
            "Otherwise only if you know what you're doing press Continue.",
            "Reset", () =>
            {
                ResetSequencer(false);
            },
            "Continue", () =>
            {
                ResetSequencer(true);
            });
    }

    public static void PromptDelete()
    {
        SalmonDialog.PromptDialog(
                "Delete", "Delete " + SalmonVaultManager.Instance.SelectedFiles.Count + " item(s)?",
                "Ok",
                () => SalmonVaultManager.Instance.DeleteSelectedFiles(),
                "Cancel", null);
    }

    public static void PromptExit()
    {
        SalmonDialog.PromptDialog("Exit",
            "Exit App",
            "Ok",
            () =>
            {
                SalmonVaultManager.Instance.CloseVault();
                Environment.Exit(0);
            },
            "Cancel"
        );
    }

    public static void PromptAnotherProcessRunning()
    {
        SalmonDialog.PromptDialog("File Search", "Another process is running");
    }

    public static void PromptSearch()
    {
        SalmonDialog.PromptEdit("Search", "Keywords",
            (value, isChecked) =>
            {
                SalmonVaultManager.Instance.Search(value, isChecked);
            }, option: "Any Term");
    }

    public static void PromptAbout()
    {
        SalmonDialog.PromptDialog("About", SalmonConfig.APP_NAME
            + " v" + Assembly.GetExecutingAssembly().GetName().Version.ToString() + "\n" +
            SalmonConfig.ABOUT_TEXT,
            "Project Website", () =>
            {
                try
                {
                    URLUtils.GoToUrl(SalmonConfig.SourceCodeURL);
                }
                catch (Exception)
                {
                    SalmonDialog.PromptDialog("Error", "Could not open Url: " + SalmonConfig.SourceCodeURL);
                }
            }, "Ok");
    }

    public static void PromptSelectRoot()
    {
        SalmonDialog.PromptDialog("Vault",
            "Choose a location for your vault",
            "Ok",
            PromptOpenVault,
            "Cancel"
            );
    }

    public static void PromptCreateVault()
    {
        ServiceLocator.GetInstance().Resolve<IFileDialogService>().PickFolder("Select the vault",
                SalmonSettings.GetInstance().VaultLocation, (filePath) =>
                {
                    SalmonDialogs.PromptSetPassword((string pass) =>
                                {
                                    try
                                    {
                                        SalmonVaultManager.Instance.CreateVault((string)filePath, pass);
                                        SalmonDialog.PromptDialog("Action", "Vault created, you can start importing your files");
                                    }
                                    catch (Exception e)
                                    {
                                        SalmonDialog.PromptDialog("Error", "Could not create vault: " + e.Message);
                                    }
                                });
                },
                SalmonVaultManager.REQUEST_CREATE_VAULT_DIR);
    }

    public static void PromptOpenVault()
    {
        ServiceLocator.GetInstance().Resolve<IFileDialogService>().PickFolder("Select the vault",
                SalmonSettings.GetInstance().VaultLocation, (filePath) =>
                        SalmonVaultManager.Instance.OpenVault((string)filePath),
                SalmonVaultManager.REQUEST_OPEN_VAULT_DIR);
    }

    public static void PromptImportFiles()
    {
        ServiceLocator.GetInstance().Resolve<IFileDialogService>().OpenFiles("Select files to import",
                    null, SalmonSettings.GetInstance().LastImportDir, (obj) =>
                    {
                        string[] files = (string[])obj;
                        List<IRealFile> filesToImport = new List<IRealFile>();
                        IFileService fileService = ServiceLocator.GetInstance().Resolve<IFileService>();
                        foreach (string file in files)
                        {
                            filesToImport.Add(fileService.GetFile(file, false));
                        }
                        SalmonSettings.GetInstance().LastImportDir = new FileInfo(files[0]).Directory.FullName;
                        SalmonVaultManager.Instance.ImportFiles(filesToImport.ToArray(),
                            SalmonVaultManager.Instance.CurrDir, SalmonSettings.GetInstance().DeleteAfterImport, (SalmonFile[] importedFiles) =>
                            {
                                SalmonVaultManager.Instance.Refresh();
                            });
                    }, SalmonVaultManager.REQUEST_IMPORT_FILES);
    }

    public static void PromptNewFolder()
    {
        SalmonDialog.PromptEdit("Create Folder",
                "Folder Name",
                (folderName, isChecked) =>
                {
                    try
                    {
                        SalmonVaultManager.Instance.CurrDir.CreateDirectory(folderName, null, null);
                        SalmonVaultManager.Instance.Refresh();
                    }
                    catch (Exception exception)
                    {
                        exception.PrintStackTrace();
                        if (!SalmonVaultManager.Instance.HandleException(exception))
                        {
                            SalmonDialog.PromptDialog("Error", "Could Not Create Folder: " + exception.Message);
                        }
                    }
                }, "New Folder", true, false, false, null);
    }

    public static void PromptRenameFile(SalmonFile ifile)
    {
        String currentFilename = "";
        try
        {
            currentFilename = ifile.BaseName;
        }
        catch (Exception ex)
        {
            ex.PrintStackTrace();
        }

        try
        {
            SalmonDialog.PromptEdit("Rename",
                    "New filename",
                    (newFilename, isChecked) =>
                    {
                        if (newFilename == null)
                            return;
                        try
                        {
                            SalmonVaultManager.Instance.RenameFile(ifile, newFilename);
                        }
                        catch (Exception exception)
                        {
                            exception.PrintStackTrace();
                            if (!SalmonVaultManager.Instance.HandleException(exception))
                            {
                                SalmonDialog.PromptDialog("Error: " + exception.Message);
                            }
                        }
                        SalmonVaultManager.Instance.UpdateListItem(ifile);
                    }, currentFilename, true, false, false, null);
        }
        catch (Exception exception)
        {
            exception.PrintStackTrace();
        }
    }
}
