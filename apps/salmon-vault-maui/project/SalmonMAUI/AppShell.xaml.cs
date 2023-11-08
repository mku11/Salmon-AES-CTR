using Microsoft.Maui.Controls;
using Salmon.Vault.View;

namespace Salmon.Vault.MAUI;

public partial class AppShell : Shell
{

	public AppShell()
	{
		InitializeComponent();
        RegisterRoutes();
		SetIcon();
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
