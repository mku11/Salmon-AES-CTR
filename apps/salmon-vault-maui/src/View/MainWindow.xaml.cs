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
#if ANDROID
using AndroidX.Lifecycle;
#endif
using Microsoft.Maui.Controls;
using Microsoft.Maui.Graphics;
using Salmon.Vault.Services;
using Salmon.Vault.Utils;
using Salmon.Vault.ViewModel;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using System.Windows.Input;
using static Salmon.Vault.Services.IKeyboardService;
using static Salmon.Vault.View.ViewHandlers;

namespace Salmon.Vault.View;

/// <summary>
/// Interaction logic for MainWindow.xaml
/// </summary>
public partial class MainWindow : ContentPage
{
    public static MainViewModel ViewModel;
    public ViewHandlers ViewHandlers { get; set; }

    public delegate void AttachViewModel(MainViewModel viewModel);
    public static AttachViewModel OnAttachViewModel;

    private HashSet<string> keysPressed = new HashSet<string>();
    private HashSet<MetaKey> metaKeysPressed = new HashSet<MetaKey>();

    public static Window ActiveWindow { get; private set; }
    private SalmonFileViewModel lastSelectedItem;

    public MainWindow()
    {
        InitializeComponent();

        ViewModel = (MainViewModel)BindingContext;

        // attach the viewmodel to platform specific code
        OnAttachViewModel(ViewModel);

        this.Loaded += MainWindow_Loaded;

        ViewHandlers = new ViewHandlers();
        ViewHandlers.OnContextMenu += OnContextMenu;
        ViewModel.PropertyChanged += ViewModel_PropertyChanged;

        SetupKeyboardShortcuts();
    }

    private void MainWindow_Loaded(object sender, EventArgs e)
    {
        MainWindow.ActiveWindow = this.Window;
        DataGrid.Focus();
        ViewModel.OnShow();
    }

    #region WORKAROUNDS for list multiple selection and keyboard accelerator

    private void ViewModel_PropertyChanged(object sender, System.ComponentModel.PropertyChangedEventArgs e)
    {
        if (e.PropertyName == "CurrentItem") ScrollTo(ViewModel.CurrentItem);
    }

    private void SetupKeyboardShortcuts()
    {
        ServiceLocator.GetInstance().Resolve<IKeyboardService>().OnKey += MainWindow_OnKey;
        ServiceLocator.GetInstance().Resolve<IKeyboardService>().OnMetaKey += MainWindow_OnMetaKey;
    }

    private void MainWindow_OnMetaKey(object sender, MetaKeyEventArgs e)
    {
        if (e.Down)
        {
            metaKeysPressed.Add(e.MetaKey);
            DetectShortcuts();
        }
        else
            metaKeysPressed.Remove(e.MetaKey);
    }

    private void MainWindow_OnKey(object sender, KeyEventArgs e)
    {
        if (e.Down)
        {
            keysPressed.Add(e.Key);
            DetectShortcuts();
        } else if (!e.Down && e.Key.Equals("Enter"))
        {
            // workaround for Enter
            keysPressed.Add(e.Key);
            DetectShortcuts();
            keysPressed.Remove(e.Key);
        }
        else
            keysPressed.Remove(e.Key);
    }

    private ICommand _clickCommand;
    public ICommand ClickCommand
    {
        get
        {
            if (_clickCommand == null)
            {
                _clickCommand = new RelayCommand<ActionType>(OnCommandClicked);
            }
            return _clickCommand;
        }
    }

    private void OnCommandClicked(ActionType type)
    {
        ViewModel.OnCommandClicked(type);
        // we set the focus to the datagrid to be able to capture keyboard shortcuts
        DataGrid.Focus();
    }

    public class RelayCommand<T> : ICommand
    {
        readonly Action<T> command;


        public RelayCommand(Action<T> command)
        {
            this.command = command;
        }

        public event EventHandler CanExecuteChanged;

        public bool CanExecute(object parameter)
        {
            return true;
        }

        public void Execute(object parameter)
        {
            if (command != null)
            {
                command((T)parameter);
            }
        }
    }

    private void DetectShortcuts()
    {
        if (DataGrid.IsFocused)
        {
            if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("R"))
                ViewModel.OnCommandClicked(ActionType.REFRESH);
            else if (keysPressed.Contains("Back"))
                ViewModel.OnCommandClicked(ActionType.BACK);
            else if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("O"))
                ViewModel.OnCommandClicked(ActionType.OPEN_VAULT);
            else if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("N"))
                ViewModel.OnCommandClicked(ActionType.CREATE_VAULT);
            else if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("L"))
                ViewModel.OnCommandClicked(ActionType.CLOSE_VAULT);
            else if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("I"))
                ViewModel.OnCommandClicked(ActionType.IMPORT);
            else if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("E"))
                ViewModel.OnCommandClicked(ActionType.EXPORT);
            else if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("U"))
                ViewModel.OnCommandClicked(ActionType.EXPORT_AND_DELETE);
            else if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("C"))
                ViewModel.OnCommandClicked(ActionType.COPY);
            else if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("X"))
                ViewModel.OnCommandClicked(ActionType.CUT);
            else if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("V"))
                ViewModel.OnCommandClicked(ActionType.PASTE);
            else if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("F"))
                ViewModel.OnCommandClicked(ActionType.SEARCH);
            else if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("A"))
            {
                ViewModel.IsMultiSelection = true;
                DataGrid.Focus();
                foreach (SalmonFileViewModel vm in ViewModel.FileItemList)
                    DataGrid.SelectedItems.Add(vm);
            }
            else if (keysPressed.Contains("Delete"))
                ViewModel.OnCommandClicked(ActionType.DELETE);
            else if (keysPressed.Contains("Escape"))
            {
                keysPressed.Clear();
                metaKeysPressed.Clear();
                ViewModel.IsMultiSelection = false;
                ViewModel.Cancel();
            }
            else if (keysPressed.Contains("Enter"))
            {
                if (DataGrid.SelectionMode == SelectionMode.Single && DataGrid.SelectedItem != null)
                    ViewModel.OpenItem((SalmonFileViewModel)DataGrid.SelectedItem);
            }

        }

    }
    #endregion

    #region WORKAROUND for press and hold context menu for the list
    private void OnContextMenu(object sender, ContextMenuEventArgs e)
    {
        WindowUtils.RunOnMainThread(() =>
        {
            if (DataGrid.SelectionMode == SelectionMode.Multiple)
            {
                DataGrid.SelectionMode = SelectionMode.Single;
                DataGrid.SelectedItems.Clear();
            }

            if (e.Index >= 0)
                DataGrid.SelectedItem = DataGrid.ItemsSource.Cast<SalmonFileViewModel>().ToArray()[e.Index];
            else if (e.Context != null)
            {
                SalmonFileViewModel viewModel = (SalmonFileViewModel)e.Context;
                SelectItem(viewModel);
            }
        });
    }

    private void SelectItem(SalmonFileViewModel viewModel)
    {
        // TODO: do we still need this workaround?
#if ANDROID
        if (DataGrid.SelectionMode == SelectionMode.Multiple)
        {
            if (DataGrid.SelectedItems.Contains(viewModel))
                DataGrid.SelectedItems.Remove(viewModel);
            else
                DataGrid.SelectedItems.Add(viewModel);
        }
        else
        {
            DataGrid.SelectedItem = viewModel;
        }
#else
        DataGrid.SelectedItem = viewModel;
#endif
    }
    #endregion

    #region WORKAROUND for Android selection and multi selection with keyboard ctrl and shift
    private void TapGestureRecognizer_SingleTapped(object sender, EventArgs e)
    {
        SalmonFileViewModel nSelectedItem = (SalmonFileViewModel)(sender as Grid).BindingContext;
        if (!metaKeysPressed.Contains(MetaKey.Ctrl) && DataGrid.SelectionMode == SelectionMode.Multiple)
        {
            lastSelectedItem = nSelectedItem;
        }
        else if (metaKeysPressed.Contains(MetaKey.Ctrl) && DataGrid.SelectionMode == SelectionMode.Single)
        {
            DataGrid.SelectionMode = SelectionMode.Multiple;
            DataGrid.SelectedItems.Add(lastSelectedItem);
            DataGrid.SelectedItems.Add(nSelectedItem);
            lastSelectedItem = nSelectedItem;
        }
        else if (metaKeysPressed.Contains(MetaKey.Shift) && DataGrid.SelectionMode == SelectionMode.Single)
        {
            DataGrid.SelectionMode = SelectionMode.Multiple;
            DataGrid.SelectedItems.Add(lastSelectedItem);
            SalmonFileViewModel startItem = lastSelectedItem;
            SalmonFileViewModel endItem = nSelectedItem;
            lastSelectedItem = nSelectedItem;
            WindowUtils.RunOnMainThread(() =>
            {
                SelectItemsRange(startItem, endItem);
            }, 200);
        }
        else
        {
            lastSelectedItem = nSelectedItem;
#if IOS || ANDROID
            SelectItem(lastSelectedItem);
            Task.Run(() =>
            {
                ViewModel.OpenItem(lastSelectedItem);
            });
#endif
        }
    }

    public void SelectItemsRange(SalmonFileViewModel item1, SalmonFileViewModel item2)
    {
        int pos1 = ViewModel.FileItemList.IndexOf(item1);
        int pos2 = ViewModel.FileItemList.IndexOf(item2);
        if (pos1 == -1 || pos2 == -1)
            return;
        for (int i = Math.Min(pos1, pos2); i <= Math.Max(pos1, pos2); i++)
        {
            DataGrid.SelectedItems.Add(ViewModel.FileItemList[i]);
        }
    }
    #endregion

    #region WORKAROUND for WinUI DoubleClick to open item
    private void TapGestureRecognizer_DoubleTapped(object sender, EventArgs e)
    {
#if WINDOWS || MACCATALYST
        SalmonFileViewModel viewModel = (SalmonFileViewModel)(sender as Grid).BindingContext;
        ViewModel.OpenItem(viewModel);
#endif
    }
    #endregion

    #region Menu flyout passthrough
    // TODO: bind directly and refactor to the MainViewModel, note that CommandParameter doesn't work
    private void MenuFlyoutItem_OnViewItem(object sender, EventArgs e)
    {
        SalmonFileViewModel viewModel = (SalmonFileViewModel)(sender as MenuFlyoutItem).BindingContext;
        ViewModel.OpenItem(viewModel);
    }

    private void MenuFlyoutItem_OnViewAsTextItem(object sender, EventArgs e)
    {
        SalmonFileViewModel viewModel = (SalmonFileViewModel)(sender as MenuFlyoutItem).BindingContext;
        ViewModel.StartTextEditor(viewModel);
    }

    private void MenuFlyoutItem_OnCopyItem(object sender, EventArgs e)
    {
        ViewModel.OnCopy();
    }

    private void MenuFlyoutItem_OnCutItem(object sender, EventArgs e)
    {
        ViewModel.OnCut();
    }

    private void MenuFlyoutItem_OnDeleteItem(object sender, EventArgs e)
    {
        ViewModel.PromptDelete();
    }

    private void MenuFlyoutItem_OnRenameItem(object sender, EventArgs e)
    {
        ViewModel.RenameFile((SalmonFileViewModel)DataGrid.SelectedItem);
    }

    private void MenuFlyoutItem_OnExportItem(object sender, EventArgs e)
    {
        ViewModel.ExportSelectedFiles(false);
    }

    private void MenuFlyoutItem_OnExportAndDeleteItem(object sender, EventArgs e)
    {
        ViewModel.ExportSelectedFiles(true);
    }

    private void MenuFlyoutItem_OnPropertiesItem(object sender, EventArgs e)
    {
        SalmonFileViewModel viewModel = (SalmonFileViewModel)(sender as MenuFlyoutItem).BindingContext;
        ViewModel.ShowProperties(viewModel);
    }
    #endregion

    protected override bool OnBackButtonPressed()
    {
        ViewModel.OnBack();
        return true;
    }

    #region WORKAROUND for Android highlighting selection with different color
    private void DataGrid_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (DataGrid == null || DataGrid.ItemsSource == null)
            return;
        foreach (SalmonFileViewModel vm in DataGrid.ItemsSource)
        {
            if (vm != DataGrid.SelectedItem && !DataGrid.SelectedItems.Contains(vm))
            {
                vm.ItemBackgroundColor = Colors.Transparent;
                ViewModel.Select(vm, false);
            }
            else
            {
                vm.ItemBackgroundColor = Color.FromHex("#445566");
                ViewModel.Select(vm, true);
            }
        }
    }
    #endregion

    private void ScrollTo(SalmonFileViewModel item)
    {
        WindowUtils.RunOnMainThread(() =>
        {
            int position = DataGrid.ItemsSource.Cast<SalmonFileViewModel>().ToList().IndexOf(item);
            if (position >= 0)
            {
                DataGrid.ScrollTo(position);
                DataGrid.SelectedItem = item;
            }
            DataGrid.Focus();
        });
    }

    private void NameHeader_SingleTapped(object sender, EventArgs e)
    {
        ViewModel.SortByName();
    }

    private void DateHeader_SingleTapped(object sender, EventArgs e)
    {
        ViewModel.SortByDate();
    }

    private void TypeHeader_SingleTapped(object sender, EventArgs e)
    {
        ViewModel.SortByType();
    }

    private void SizeHeader_SingleTapped(object sender, EventArgs e)
    {
        ViewModel.SortBySize();
    }

}
