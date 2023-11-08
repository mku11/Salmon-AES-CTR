using Microsoft.Maui;
using Microsoft.Maui.Hosting;
using Mku.File;
using Mku.SalmonFS;
using Salmon.Vault.Services;
using Salmon.Vault.View;
using Salmon.Vault.ViewModel;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace Salmon.Vault.MAUI.WinUI;

/// <summary>
/// Provides application-specific behavior to supplement the default Application class.
/// </summary>
public partial class App : MauiWinUIApplication
{
    public MainViewModel ViewModel { get; private set; }

    /// <summary>
    /// Initializes the singleton application object.  This is the first line of authored code
    /// executed, and as such is the logical equivalent of main() or WinMain().
    /// </summary>
    public App()
    {
        this.InitializeComponent();
        SetupServices();
    }

    private void SetupServices()
    {
        SalmonDriveManager.VirtualDriveClass = typeof(DotNetDrive);
        ServiceLocator.GetInstance().Register(typeof(IFileService), new WinFileService());
        SetupWebBrowser();
        MainWindow.OnAttachViewModel = AttachViewModel;
    }

    private void SetupWebBrowser()
    {
        ServiceLocator.GetInstance().Register(typeof(IWebBrowserService), new WinBrowserService());
    }

    protected override MauiApp CreateMauiApp() => MauiProgram.CreateMauiApp();

    public void AttachViewModel(MainViewModel viewModel)
    {
        this.ViewModel = viewModel;

        WinFileDialogService fileDialogService = new WinFileDialogService();
        ServiceLocator.GetInstance().Register(typeof(IFileDialogService), fileDialogService);

        WinKeyboardService keyboardService = new WinKeyboardService();
        ServiceLocator.GetInstance().Register(typeof(IKeyboardService), keyboardService);
        
    }

}

