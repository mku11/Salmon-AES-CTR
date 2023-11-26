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
using Mku.Utils;
using Salmon.Vault.Utils;
using Salmon.Vault.WPF;
using System;
using System.Collections.Generic;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using static System.Net.Mime.MediaTypeNames;
using Label = System.Windows.Controls.Label;
using Orientation = System.Windows.Controls.Orientation;

namespace Salmon.Vault.Dialog;

public class SalmonDialog : System.Windows.Window
{
    private Dictionary<ButtonType, Button> buttons = new Dictionary<ButtonType, Button>();
    public FrameworkElement _content;
    private StackPanel stackPanel;
    private StackPanel buttonsLayout;
    private StackPanel contentLayout;
    public enum ButtonType
    {
        Ok, Cancel, None
    }
    public class Button
    {
        internal System.Windows.Controls.Button button = new System.Windows.Controls.Button();
        public SalmonDialog alert;
        public object Content
        {
            get => button.Content;
            set
            {
                Label label = new Label();
                label.Content = value;
                button.Content = label;
            }
        }

        public event RoutedEventHandler Click;
        public Button(SalmonDialog alert)
        {
            this.alert = alert;
            button.Click += (object sender, RoutedEventArgs e) =>
            {
                try
                {
                    if (Click != null)
                        Click.Invoke(sender, e);
                }
                catch (Exception)
                {

                }
                if (!e.Handled)
                    alert.Close();
            };
        }
    }
    public new FrameworkElement Content
    {
        get => _content;
        set
        {
            _content = value;
            SetLayout();
        }
    }

    public SalmonDialog(string content)
    {
        buttons[ButtonType.Ok] = new Button(this);
        SetTextContent(content);
        SetLayout();
    }

    public SalmonDialog(string content, ButtonType buttonType)
    {
        buttons[buttonType] = new Button(this);
        SetTextContent(content);
        SetLayout();
    }

    public SalmonDialog(string content, ButtonType buttonType1, ButtonType buttonType2)
    {
        buttons[buttonType1] = new Button(this);
        buttons[buttonType2] = new Button(this);
        SetTextContent(content);
        SetLayout();
    }

    public SalmonDialog(string content, ButtonType buttonType1, ButtonType buttonType2, ButtonType buttonType3)
    {
        buttons[buttonType1] = new Button(this);
        buttons[buttonType2] = new Button(this);
        buttons[buttonType3] = new Button(this);
        SetTextContent(content);
        SetLayout();
    }

    private void SetLayout()
    {
        Background = (Brush)App.Current.Resources["SalmonBackground"];
        Foreground = Brushes.White;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        if (stackPanel == null)
        {
            stackPanel = new StackPanel()
            {
                Orientation = Orientation.Vertical,
                Margin = new Thickness(4),
            };
        }
        stackPanel.Children.Clear();

        if (contentLayout == null)
        {
            contentLayout = new StackPanel()
            {
                Orientation = Orientation.Vertical,
                MinHeight = 60,
                MinWidth = 250
            };
        }
        contentLayout.Children.Clear();
        contentLayout.Children.Add(Content);

        if (buttonsLayout == null)
        {
            buttonsLayout = new StackPanel()
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = System.Windows.HorizontalAlignment.Right
            };
        }
        buttonsLayout.Children.Clear();
        foreach (ButtonType buttonType in buttons.Keys)
        {
            if (buttonType == ButtonType.Ok && buttons[buttonType].Content == null)
                buttons[buttonType].Content = "Ok";
            if (buttonType == ButtonType.Cancel && buttons[buttonType].Content == null)
                buttons[buttonType].Content = "Cancel";
            buttons[buttonType].button.MinWidth = 80;
            buttonsLayout.Children.Add(buttons[buttonType].button);
        }

        stackPanel.Children.Add(contentLayout);
        stackPanel.Children.Add(buttonsLayout);
        base.Content = stackPanel;
        SizeToContent = SizeToContent.WidthAndHeight;
    }

    public Button GetButton(ButtonType buttonType)
    {
        return buttons[buttonType];
    }

    private void SetTextContent(string content)
    {
        Label text = new Label();
        text.Content = content;
        Content = text;
    }

    public new void Show()
    {
        foreach (ButtonType btn in buttons.Keys)
        {
            if (btn == ButtonType.Ok)
            {
                buttons[btn].button.Focus();
                buttons[btn].button.BorderThickness = new Thickness(2);
            }
        }
        ShowDialog();
    }

    public static void PromptEdit(string title, string msg, Action<string, bool> OnEdit,
        string value = "", bool isFileName = false, bool readOnly = false, bool isPassword = false,
        string option = null)
    {
        WindowUtils.RunOnMainThread(() =>
        {
            SalmonDialog alert = new SalmonDialog("", ButtonType.Ok, ButtonType.Cancel);
            alert.Title = title;
            Label msgText = new Label();
            msgText.Content = msg;
            Control valueText;
            if (isPassword)
            {
                PasswordBox passText = new PasswordBox();
                passText.VerticalContentAlignment = VerticalAlignment.Center;
                passText.Password = value;
                valueText = passText;
            }
            else
            {
                TextBox text = new TextBox();
                text.Text = value;
                if (readOnly)
                    text.IsReadOnly = true;
                valueText = text;
            }
            CheckBox optionCheckBox = new CheckBox();
            StackPanel panel = new StackPanel();
            panel.Margin = new Thickness(10);
            panel.Children.Add(msgText);
            panel.Children.Add(valueText);
            if (option != null)
            {
                optionCheckBox.Content = option;
                panel.Children.Add(optionCheckBox);
            }
            alert.Content = panel;
            alert.MinWidth = 280;
            alert.MinHeight = 150;

            Button btOk = alert.GetButton(ButtonType.Ok);
            Action<object, RoutedEventArgs> OnOk = (sender, e) =>
            {
                if (OnEdit != null)
                {
                    string value = isPassword ? (valueText as PasswordBox).Password : (valueText as TextBox).Text;
                    OnEdit.Invoke(value, (bool)optionCheckBox.IsChecked);
                }
            };
            btOk.Click += (sender, e) => OnOk.Invoke(null, e);
            valueText.KeyDown += (sender, e) =>
            {
                if (e.Key == System.Windows.Input.Key.Enter)
                {
                    OnOk.Invoke(null, e);
                    if (!e.Handled)
                        alert.Close();
                }
            };

            valueText.Focus();
            if (isFileName)
            {
                string ext = SalmonFileUtils.GetExtensionFromFileName(value);
                if (ext != null && ext.Length > 0 && !isPassword)
                {
                    (valueText as TextBox).Select(0, value.Length - ext.Length - 1);
                }
                else
                    (valueText as TextBox).Select(0, value.Length);
            }
            else if (!isPassword)
            {
                (valueText as TextBox).SelectAll();
            }
            alert.ShowDialog();
        });
    }

    public static void PromptDialog(string title, string body,
                                    string buttonLabel1 = "Ok", Action buttonListener1 = null,
                                    string buttonLabel2 = null, Action buttonListener2 = null)
    {
        WindowUtils.RunOnMainThread(() =>
        {
            SalmonDialog alert;
            if (buttonLabel2 != null)
                alert = new SalmonDialog(body, ButtonType.Ok, ButtonType.Cancel);
            else
                alert = new SalmonDialog(body, ButtonType.Ok);
            Button btnOk = alert.GetButton(ButtonType.Ok);
            btnOk.Content = buttonLabel1;
            if (buttonListener1 != null)
            {
                btnOk.Click += (object sender, RoutedEventArgs e) =>
                {
                    buttonListener1.Invoke();
                };
            }
            if (buttonLabel2 != null)
            {
                Button btnCancel = alert.GetButton(ButtonType.Cancel);
                btnCancel.Content = buttonLabel2;
                if (buttonListener2 != null)
                {
                    btnCancel.Click += (object sender, RoutedEventArgs e) =>
                    {
                        buttonListener2.Invoke();
                    };
                }
            }

            if (title != null)
                alert.Title = title;
            Label ContentText = new Label();
            ContentText.Content = body;
            alert.Content = ContentText;
            alert.ShowDialog();
        });
    }
	
	public static void PromptDialog(string body)
    {
        WindowUtils.RunOnMainThread(() =>
        {
            PromptDialog("", body, "Ok");
        });
    }
}