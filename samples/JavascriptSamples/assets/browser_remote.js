import { Sample, RemoteSample } from './common.js';
import { JsHttpFile } from '../lib/salmon-fs/file/js_http_file.js';

var output = document.getElementById("text-edit");

export async function listRemoteVault() {
	let vaultURL = document.getElementById("vault-url");
	if(vaultURL == "") {
		window.alert("Type in a remote Vault URL");
		return;
	}
	let remoteDir = new JsHttpFile(vaultURL.value);
	
	let vaultPassword = document.getElementById("vault-password");
	if(vaultPassword == "") {
		window.alert("Type in a password");
		return;
	}
	let password = vaultPassword.value;
	
	try {
		await RemoteSample.listRemoteVault(remoteDir, password);
	} catch (ex) {
		console.error(ex);
		output.value += ex + "\n";
	}
}

window.listRemoteVault = listRemoteVault;