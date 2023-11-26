using Microsoft.Maui.Controls;
using Salmon.Vault.Services;
using Salmon.Vault.View;
using System;

namespace Salmon.Vault.MAUI;

public partial class AppShell : Shell
{

	public AppShell()
	{
		InitializeComponent();
        RegisterRoutes();
        SetupServices();
		SetIcon();
	}

    private void SetupServices()
    {
        ServiceLocator.GetInstance().Register(typeof(ISettingsService), new MAUISettingsService());
    }

    private void RegisterRoutes()
    {
        Routing.RegisterRoute(nameof(MainWindow), typeof(MainWindow));
        Routing.RegisterRoute(nameof(SettingsViewer), typeof(SettingsViewer));
        Routing.RegisterRoute(nameof(TextEditor), typeof(TextEditor));
        Routing.RegisterRoute(nameof(ImageViewer), typeof(ImageViewer));
        Routing.RegisterRoute(nameof(ContentViewer), typeof(ContentViewer));
    }

    // waiting for fix https://github.com/dotnet/maui/issues/6908
    private void SetIcon()
    {
        
    }
}
