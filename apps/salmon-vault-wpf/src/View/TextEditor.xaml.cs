﻿/*
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
using Salmon.Vault.ViewModel;
using Salmon.Vault.Utils;
using Salmon.Vault.WPF;
using System.Windows.Controls;
using System.Windows.Media;

namespace Salmon.Vault.View
{
    public partial class TextEditor : System.Windows.Window
    {
        public TextEditorViewModel ViewModel { get; private set; }
        public TextEditor(SalmonFileViewModel file)
        {
            InitializeComponent();
            ViewModel = (TextEditorViewModel)DataContext;
            Loaded += TextEditor_Loaded;
            ViewModel.SelectAndScrollTo = Select;
            ViewModel.PromptSearch = PromptSearch;
            Load(file);
        }

        private void TextEditor_Loaded(object sender, System.Windows.RoutedEventArgs e)
        {
            WindowUtils.SetChildBackground(MainMenu, (obj) =>
            {
                if (obj.GetType() == typeof(Border))
                    ((Border)obj).Background = (Brush) App.Current.Resources["SalmonBackground"];
            });
        }

        public void Load(SalmonFileViewModel file)
        {
            ViewModel.Load(file);
        }

        public void Select(int start, int length)
        {

            WindowUtils.RunOnMainThread(() =>
            {
                TextArea.Focus();
                TextArea.SelectionStart = start;
                TextArea.SelectionLength = length;
                SearchText.Focus();
            });
        }

        public void Search_Click(object sender, System.Windows.RoutedEventArgs e)
        {
            ViewModel.OnSearch(SearchText.Text, TextArea.SelectionLength > 0 ? TextArea.CaretIndex + 1 : TextArea.CaretIndex);
        }

        private void SearchText_KeyUp(object sender, System.Windows.Input.KeyEventArgs e)
        {
            if (e.Key == System.Windows.Input.Key.Enter)
            {
                ViewModel.OnSearch(SearchText.Text, TextArea.SelectionLength > 0 ? TextArea.CaretIndex + 1 : TextArea.CaretIndex);
            }
        }

        private void PromptSearch()
        {
            SearchText.Focus();
            SearchText.SelectAll();
        }

        private void MenuItemSearch_Click(object sender, System.Windows.RoutedEventArgs e)
        {
            PromptSearch();
        }
    }
}