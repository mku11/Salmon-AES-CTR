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
using Salmon.Vault.Prefs;
using Salmon.Vault.ViewModel;
using Salmon.Vault.Utils;
using Salmon.Vault.WPF;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using static Salmon.Vault.ViewModel.MainViewModel;
using Salmon.Vault.Settings;
using System.Collections.ObjectModel;

namespace Salmon.Vault.View
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : System.Windows.Window
    {
        public MainViewModel ViewModel { get; set; }
        public MainWindow()
        {
            InitializeComponent();
            ViewModel = ((MainViewModel)DataContext);
            SetWindow();
            Loaded += MainWindow_Loaded;
            ViewModel.OpenImageViewer = (item) => OpenImageViewer(item);
            ViewModel.OpenTextEditor = (item) => OpenTextEditor(item);
            ViewModel.OpenMediaPlayer = (item) => OpenMediaPlayer(item);
            ViewModel.OpenContentViewer = (item) => OpenContentViewer(item);
            ViewModel.OpenSettingsViewer = () => OpenSettings();
            ViewModel.PropertyChanged += ViewModel_PropertyChanged;
        }

        private void ViewModel_PropertyChanged(object sender, System.ComponentModel.PropertyChangedEventArgs e)
        {
            if (e.PropertyName == "CurrentItem") ScrollTo(ViewModel.CurrentItem);
        }

        /// <summary>
        /// Workaround for selection
        /// </summary>
        private void SetWindow()
        {
            KeyDown += (object sender, KeyEventArgs e) =>
            {
                if (e.Key == Key.Down && !DataGrid.IsFocused)
                {
                    e.Handled = true;
                    DataGrid.SelectedIndex = 0;
                    DataGridRow row = (DataGridRow)DataGrid.ItemContainerGenerator.ContainerFromIndex(0);
                    if (row != null)
                        row.MoveFocus(new TraversalRequest(FocusNavigationDirection.Next));
                }
            };
        }

        /// <summary>
        /// Workaround for background color
        /// </summary>
        /// <param name="sender"></param>
        /// <param name="e"></param>
        private void MainWindow_Loaded(object sender, RoutedEventArgs e)
        {
            WindowUtils.SetChildBackground(MainMenu, (obj) =>
            {
                if (obj.GetType() == typeof(Border))
                    ((Border)obj).Background = (Brush)App.Current.Resources["SalmonBackground"];
            });
            ViewModel.OnShow();
        }

        public void OpenImageViewer(SalmonFileViewModel viewModel)
        {
            ImageViewer imageViewer = new ImageViewer(viewModel);
            imageViewer.ShowDialog();
        }

        public void OpenTextEditor(SalmonFileViewModel viewModel)
        {
            TextEditor textEditor = new TextEditor(viewModel);
            textEditor.ShowDialog();
        }

        public void OpenMediaPlayer(SalmonFileViewModel viewModel)
        {
            MediaPlayer mediaPlayer = new MediaPlayer(viewModel);
            mediaPlayer.ShowDialog();
        }

        public void OpenContentViewer(SalmonFileViewModel viewModel)
        {
            ContentViewer contentViewer = new ContentViewer(viewModel);
            contentViewer.ShowDialog();
        }

        public static void OpenSettings()
        {
            SettingsViewer settings = new SettingsViewer();
            settings.ShowDialog();

            SalmonSettings.GetInstance().AesType = settings.ViewModel.AesTypeSelected;
            SalmonSettings.GetInstance().SequencerAuthType = settings.ViewModel.AuthTypeSelected;
            SalmonSettings.GetInstance().DeleteAfterImport = settings.ViewModel.DeleteSourceAfterImport;
            SalmonPreferences.SavePrefs();
        }

        private void ScrollTo(SalmonFileViewModel item)
        {
            if (item == null)
                return;
            WindowUtils.RunOnMainThread(() =>
            {
                int index = DataGrid.ItemsSource.Cast<SalmonFileViewModel>().ToList().IndexOf(item);
                if (index < 0)
                    return;
                DataGrid.ScrollIntoView(item);
                DataGrid.Focus();
                DataGrid.SelectedItem = item;
                if (index >= 0)
                {
                    DataGridRow row = (DataGridRow)DataGrid.ItemContainerGenerator
                        .ContainerFromIndex(DataGrid.SelectedIndex);
                    if (row != null)
                        row.MoveFocus(new TraversalRequest(FocusNavigationDirection.Next));
                }
            }, 200);
        }

        private void MenuItem_View(object sender, RoutedEventArgs e)
        {
            ViewModel.OpenItem((SalmonFileViewModel)DataGrid.SelectedItem);
        }

        private void MenuItem_ViewAsText(object sender, RoutedEventArgs e)
        {
            ViewModel.StartTextEditor((SalmonFileViewModel)DataGrid.SelectedItem);
        }

        private void MenuItem_Copy(object sender, RoutedEventArgs e)
        {
            ViewModel.OnCopy();
        }

        private void MenuItem_Cut(object sender, RoutedEventArgs e)
        {
            ViewModel.OnCut();
        }

        private void MenuItem_Delete(object sender, RoutedEventArgs e)
        {
            ViewModel.PromptDelete();
        }

        private void MenuItem_Rename(object sender, RoutedEventArgs e)
        {
            ViewModel.RenameFile((SalmonFileViewModel)DataGrid.SelectedItem);
        }

        private void MenuItem_Export(object sender, RoutedEventArgs e)
        {
            ViewModel.ExportSelectedFiles(false);
        }

        private void MenuItem_ExportAndDelete(object sender, RoutedEventArgs e)
        {
            ViewModel.ExportSelectedFiles(true);
        }

        private void MenuItem_Properties(object sender, RoutedEventArgs e)
        {
            if (DataGrid.SelectedItem != null)
                ViewModel.ShowProperties((SalmonFileViewModel)DataGrid.SelectedItem);
        }

        private void DataGrid_MouseDoubleClick(object sender, MouseButtonEventArgs e)
        {
            if (DataGrid.SelectedItem != null)
                ViewModel.OpenItem((SalmonFileViewModel)DataGrid.SelectedItem);
        }

        private void DataGrid_PreviewKeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Enter)
            {
                e.Handled = true;
                ViewModel.OnCommandClicked(ActionType.VIEW);
            }
        }

        private void DataGrid_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            ViewModel.OnSelectedItems(DataGrid.SelectedItems.Cast<SalmonFileViewModel>().ToList());
        }
    }

}
