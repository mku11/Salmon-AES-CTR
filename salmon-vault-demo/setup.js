import { setDebugConsole } from "./common/utils/debug_utils.js";
import { Handler } from "./lib/salmon-fs/service/handler.js";
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
    Handler.getInstance().setWorkerPath('service-worker.js');
    try {
        await Handler.getInstance().register();
    } catch (ex) {
        SalmonDialog.promptDialog("Error", ex);
    }
}

WindowUtils.setDefaultIconPath(SalmonConfig.APP_ICON);
setupDebug();
registerServiceWorker();