using ObjCRuntime;
using System;
using UIKit;

namespace Salmon.Vault.MAUI.IOS;

public class Program
{
    // This is the main entry point of the application.
    [STAThread]
    static void Main(string[] args)
	{
		// if you want to use a different Application Delegate class from "AppDelegate"
		// you can specify it here.
		UIApplication.Main(args, null, typeof(AppDelegate));
	}
}
