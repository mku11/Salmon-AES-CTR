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
using Salmon.Vault.Services;
using Salmon.Vault.Utils;
using Salmon.Vault.ViewModel;
using System;
using System.Collections.Generic;
using static Salmon.Vault.Services.IKeyboardService;

namespace Salmon.Vault.View;

[QueryProperty(nameof(SalmonFileViewModel), "SalmonFileViewModel")]
public partial class TextEditor : ContentPage
{
    public SalmonFileViewModel SalmonFileViewModel { get; set; }
    private HashSet<string> keysPressed = new HashSet<string>();
    private System.Collections.Generic.HashSet<MetaKey> metaKeysPressed = new HashSet<MetaKey>();
    TextEditorViewModel ViewModel { get; set; }
    public TextEditor()
    {
        InitializeComponent();
        ViewModel = (TextEditorViewModel)BindingContext;
        Loaded += TextEditor_Loaded;
        ViewModel.SelectAndScrollTo = Select;
        ViewModel.PromptSearch = PromptSearch;
        SetupKeyboardShortcuts();
    }

    private void TextEditor_Loaded(object sender, EventArgs e)
    {
        ViewModel.Load(SalmonFileViewModel);
        WindowUtils.RunOnMainThread(() =>
        {
            TextArea.Focus();
            TextArea.CursorPosition = 0;
        },500);
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
        }
        else
            keysPressed.Remove(e.Key);
    }


    private void DetectShortcuts()
    {
        if (TextArea.IsFocused)
        {
            if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("S"))
                ViewModel.OnCommandClicked(ActionType.SAVE);
            else if (metaKeysPressed.Contains(MetaKey.Ctrl) && keysPressed.Contains("F"))
                SearchText.Focus();
        }

    }

    public void Select(int start, int length)
    {
        WindowUtils.RunOnMainThread(() =>
        {
            TextArea.Focus();
            TextArea.CursorPosition = start;
            TextArea.SelectionLength = length;
        }, 500);
    }

    public void Search_Pressed(object sender, EventArgs e)
    {
        ViewModel.OnSearch(SearchText.Text, TextArea.SelectionLength > 0 ? TextArea.CursorPosition + 1 : TextArea.CursorPosition);
    }

    private void PromptSearch()
    {
        SearchText.Focus();
        SearchText.CursorPosition = 0;
        SearchText.SelectionLength = SearchText.Text.Length;
    }
}