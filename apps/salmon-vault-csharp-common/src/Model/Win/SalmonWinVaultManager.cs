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
using Mku.Sequence;
using Salmon.Vault.Config;
using Salmon.Vault.Dialog;
using Salmon.Vault.Settings;
using Salmon.Win.Sequencer;
using System;
using System.Runtime.CompilerServices;

namespace Salmon.Vault.Model.Win;

public class SalmonWinVaultManager : SalmonVaultManager
{
    new
    public static SalmonWinVaultManager Instance
    {
        [MethodImpl(MethodImplOptions.Synchronized)]
        get
        {
            if (_instance == null)
            {
                _instance = new SalmonWinVaultManager();
            }
            return (SalmonWinVaultManager)_instance;
        }
    }

    protected void SetupWinFileSequencer()
    {
        IRealFile dirFile = new DotNetFile(SequencerDefaultDirPath);
        if (!dirFile.Exists)
            dirFile.Mkdir();
        IRealFile seqFile = new DotNetFile(SequencerFilepath);
        WinFileSequencer sequencer = new WinFileSequencer(seqFile, new SalmonSequenceSerializer(), SalmonConfig.REGISTRY_CHKSUM_KEY);
        SalmonDriveManager.Sequencer = sequencer;
    }

    protected void SetupClientSequencer()
    {
        try
        {
            WinClientSequencer sequencer = new WinClientSequencer(SERVICE_PIPE_NAME);
            SalmonDriveManager.Sequencer = sequencer;
        }
        catch (Exception ex)
        {
            SalmonDialog.PromptDialog("Error", "Error during service lookup. Make sure the Salmon Service is installed and running:\n" + ex.Message);
        }
    }

    override
    public bool HandleException(Exception exception)
    {
        if (exception is WinSequenceTamperedException)
        {
            SalmonDialogs.PromptSequenceReset(ResetSequencer);
            return true;
        }
        return false;
    }

    override
    public void HandleThrowException(Exception ex)
    {
        if (ex is WinSequenceTamperedException)
            throw ex;
    }

    public void ResetSequencer(bool clearChecksumOnly)
    {
        if (SalmonDriveManager.Sequencer is WinFileSequencer)
            (SalmonDriveManager.Sequencer as WinFileSequencer).Reset(clearChecksumOnly);
        SetupSalmonManager();
    }


    override
    public void SetupSalmonManager()
    {
        try
        {
            if (SalmonDriveManager.Sequencer != null)
                SalmonDriveManager.Sequencer.Close();

            if (SalmonSettings.GetInstance().SequencerAuthType == SalmonSettings.AuthType.User)
            {
                // for windows we have a registry checksum variant
                SetupWinFileSequencer();
            }
            else if (SalmonSettings.GetInstance().SequencerAuthType == SalmonSettings.AuthType.Service)
            {
                // or the service
                SetupClientSequencer();
            }
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            SalmonDialog.PromptDialog("Error", "Error during initializing: " + e.Message);
        }
    }

}