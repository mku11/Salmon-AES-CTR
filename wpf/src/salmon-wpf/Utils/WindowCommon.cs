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
using Salmon.Alert;
using Salmon.FS;
using Salmon.Prefs;
using Salmon.Window;
using System;
using System.Windows;
using System.Windows.Controls;
using static Salmon.Alert.SalmonDialog;
using static System.Windows.Forms.VisualStyles.VisualStyleElement;
using Action = System.Action;
using Button = Salmon.Alert.SalmonDialog.Button;
using TextBox = System.Windows.Controls.TextBox;

namespace Salmon
{
    public class WindowCommon
    {
        public delegate void OnTextSubmitted(string text, Boolean option);

        //TODO: this is the standard way to get a users password though within c# we
        // shoud try using Securestring which requires other ways to capture the password from the user
        public static void PromptPassword(Action OnAuthenticationSucceded)
        {
            SalmonDialog alert = new SalmonDialog("", ButtonType.Ok, ButtonType.Cancel);
            alert.Title = "Authenticate";

            DockPanel passPanel = new DockPanel();
            passPanel.LastChildFill = true;
            Label passLabel = new Label();
            passLabel.Width = 100;
            passLabel.Content = "Password: ";
            PasswordBox pass = new PasswordBox();
            pass.VerticalContentAlignment = System.Windows.VerticalAlignment.Center;
            passPanel.Children.Add(passLabel);
            passPanel.Children.Add(pass);

            Label status = new Label();

            StackPanel panel = new StackPanel();
            panel.Margin = new System.Windows.Thickness(4);
            panel.Children.Add(passPanel);
            panel.Children.Add(status);
            alert.Content = panel;
            Button btOk = alert.GetButton(ButtonType.Ok);
            Action<object, RoutedEventArgs> OnOk = (sender, e) =>
            {
                string password = pass.Password;
                try
                {
                    SalmonDriveManager.GetDrive().Authenticate(password);
                    if (OnAuthenticationSucceded != null)
                        OnAuthenticationSucceded.Invoke();
                }
                catch (SalmonAuthException)
                {
                    e.Handled = true;
                    status.Content = "Incorrect Password";
                }
            };
            btOk.Click += (sender, e) => OnOk.Invoke(null, e);
            pass.KeyDown += (sender, e) =>
            {
                if (e.Key == System.Windows.Input.Key.Enter)
                {
                    OnOk.Invoke(null, e);
                    if (!e.Handled)
                        alert.Close();
                }
            };
            WindowUtils.RunOnMainThread(() =>
            {
                pass.Focus();
                pass.SelectAll();
            });
            alert.Show();
        }


        private static void Pass_KeyDown(object sender, System.Windows.Input.KeyEventArgs e)
        {
            throw new NotImplementedException();
        }

        public static void PromptSetPassword(Action<string> OnPasswordChanged)
        {
            SalmonDialog alert = new SalmonDialog("", ButtonType.Ok, ButtonType.Cancel);
            alert.Title = "Change Password";

            DockPanel passPanel = new DockPanel();
            passPanel.LastChildFill = true;
            Label passLabel = new Label();
            passLabel.Width = 100;
            passLabel.Content = "Type Password: ";
            PasswordBox pass = new PasswordBox();
            pass.VerticalContentAlignment = System.Windows.VerticalAlignment.Center;
            pass.Width = double.NaN;
            passPanel.Children.Add(passLabel);
            passPanel.Children.Add(pass);

            DockPanel validatePanel = new DockPanel();
            validatePanel.LastChildFill = true;
            Label validateLabel = new Label();
            validateLabel.Width = 100;
            validateLabel.Content = "Retype Password: ";
            PasswordBox validate = new PasswordBox();
            validate.Width = double.NaN;
            validate.VerticalContentAlignment = System.Windows.VerticalAlignment.Center;
            validatePanel.Children.Add(validateLabel);
            validatePanel.Children.Add(validate);

            Label status = new Label();

            StackPanel panel = new StackPanel();
            panel.Children.Add(passPanel);
            panel.Children.Add(validatePanel);
            panel.Children.Add(status);
            alert.Content = panel;
            Button btOk = alert.GetButton(ButtonType.Ok);
            btOk.Click += (object sender, System.Windows.RoutedEventArgs e) =>
            {
                string password = pass.Password;
                string passwordValidate = validate.Password;
                if (!password.Equals(passwordValidate))
                {
                    e.Handled = true;
                    status.Content = "Password do not match";
                }
                else
                {
                    if (OnPasswordChanged != null)
                        OnPasswordChanged.Invoke(password);
                }
            };
            alert.Show();
        }

        public static void PromptEdit(string title, string msg, string value, string option, 
            bool isFileName, System.Action<string, bool> OnEdit, bool readOnly)
        {
            SalmonDialog alert = new SalmonDialog("", ButtonType.Ok, ButtonType.Cancel);
            alert.Title = title;
            Label msgText = new Label();
            msgText.Content = msg;
            TextBox valueText = new TextBox();
            valueText.Text = value;
            if (readOnly)
                valueText.IsReadOnly = true;
            CheckBox optionCheckBox = new CheckBox();
            StackPanel panel = new StackPanel();
            panel.Margin = new System.Windows.Thickness(10);
            panel.Children.Add(msgText);
            panel.Children.Add(valueText);
            if (option != null)
            {
                optionCheckBox.Content = option;
                panel.Children.Add(optionCheckBox);
            }
            alert.Content = panel;
            alert.MinWidth = 340;
            alert.MinHeight = 150;

            Button btOk = (Button)alert.GetButton(ButtonType.Ok);
            Action<object, RoutedEventArgs> OnOk = (sender, e) =>
            {
                if (OnEdit != null)
                    OnEdit.Invoke(valueText.Text, (bool)optionCheckBox.IsChecked);
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
            WindowUtils.RunOnMainThread(() =>
            {
                valueText.Focus();
                if (isFileName)
                {
                    string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(value);
                    if (ext != null && ext.Length > 0)
                        valueText.Select(0, value.Length - ext.Length - 1);
                    else
                        valueText.Select(0, value.Length);
                }
                else
                {
                    valueText.SelectAll();
                }
            });
            alert.Show();
        }

        public static void PromptDialog(string title, string body,
                                        string buttonLabel1, System.Action buttonListener1,
                                        string buttonLabel2, System.Action buttonListener2)
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
                btnOk.Click += (object sender, System.Windows.RoutedEventArgs e) =>
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
                    btnCancel.Click += (object sender, System.Windows.RoutedEventArgs e) =>
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
            alert.Show();

        }


        public static void OpenVault(string dirPath)
        {
            SalmonDriveManager.OpenDrive(dirPath);
            SalmonDriveManager.GetDrive().SetEnableIntegrityCheck(true);
            Settings.Settings.GetInstance().vaultLocation = dirPath;
            Preferences.SavePrefs();
        }

        public static void CreateVault(string dirPath, string password)
        {
            SalmonDriveManager.CreateDrive(dirPath, password);
            SalmonDriveManager.GetDrive().SetEnableIntegrityCheck(true);
            Settings.Settings.GetInstance().vaultLocation = dirPath;
            Preferences.SavePrefs();
        }
    }
}