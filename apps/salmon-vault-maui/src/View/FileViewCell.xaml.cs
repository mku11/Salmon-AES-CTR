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

using Microsoft.Maui.Controls;
using Salmon.Vault.ViewModel;
using System;

namespace Salmon.Vault.View;

public partial class FileViewCell : VirtualViewCell
{
	public FileViewCell()
	{
		InitializeComponent();
	}

	private void TapGestureRecognizer_SingleTapped(object sender, EventArgs e)
	{
		MainWindow.ActiveWindow.TapGestureRecognizer_SingleTapped(sender, e);
	}

    private void TapGestureRecognizer_DoubleTapped(object sender, EventArgs e)
    {
        MainWindow.ActiveWindow.TapGestureRecognizer_DoubleTapped(sender, e);
    }

    #region Menu flyout passthrough
    private void MenuFlyoutItem_OnViewItem(object sender, EventArgs e)
    {
        SalmonFileViewModel viewModel = (SalmonFileViewModel)(sender as MenuFlyoutItem).BindingContext;
        MainWindow.ViewModel.OpenItem(viewModel);
    }

    private void MenuFlyoutItem_OnViewAsTextItem(object sender, EventArgs e)
    {
        SalmonFileViewModel viewModel = (SalmonFileViewModel)(sender as MenuFlyoutItem).BindingContext;
        MainWindow.ViewModel.StartTextEditor(viewModel);
    }

    private void MenuFlyoutItem_OnCopyItem(object sender, EventArgs e)
    {
        MainWindow.ViewModel.OnCopy();
    }

    private void MenuFlyoutItem_OnCutItem(object sender, EventArgs e)
    {
        MainWindow.ViewModel.OnCut();
    }

    private void MenuFlyoutItem_OnDeleteItem(object sender, EventArgs e)
    {
        MainWindow.ViewModel.PromptDelete();
    }

    private void MenuFlyoutItem_OnRenameItem(object sender, EventArgs e)
    {
        // TODO:
        // ViewModel.RenameFile((SalmonFileViewModel)DataGrid.SelectedItem);
    }

    private void MenuFlyoutItem_OnExportItem(object sender, EventArgs e)
    {
        MainWindow.ViewModel.ExportSelectedFiles(false);
    }

    private void MenuFlyoutItem_OnExportAndDeleteItem(object sender, EventArgs e)
    {
        MainWindow.ViewModel.ExportSelectedFiles(true);
    }

    private void MenuFlyoutItem_OnPropertiesItem(object sender, EventArgs e)
    {
        SalmonFileViewModel viewModel = (SalmonFileViewModel)(sender as MenuFlyoutItem).BindingContext;
        MainWindow.ViewModel.ShowProperties(viewModel);
    }
    #endregion
}