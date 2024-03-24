import { setDebugConsole } from "./common/utils/debug_utils.js";
import { SalmonHandler } from "./lib/salmon-fs/service/salmon_handler.js";
import { SalmonDialog } from "./vault/dialog/salmon_dialog.js";
import { WindowUtils } from "./vault/utils/window_utils.js";
import { SalmonConfig } from "./vault/config/salmon_config.js";

const DEBUG = false;
function setupDebug() {
    let debugConsole = document.getElementById("debug-console");
    let debugConsoleContainer = document.getElementById("debug-console-container");
    debugConsoleContainer.style.display = DEBUG ? "flex" : "none";
    setDebugConsole(debugConsole);
}

async function registerServiceWorker() {
    console.log("Registering handler");
    SalmonHandler.setWorkerPath('service-worker.js');
    try {
        await SalmonHandler.getInstance().register();
    } catch (ex) {
        SalmonDialog.promptDialog("Error", ex);
    }
}

WindowUtils.setDefaultIconPath(SalmonConfig.APP_ICON);
setupDebug();
registerServiceWorker();