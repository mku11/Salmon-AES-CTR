// output
let output = null;
if(typeof(document) !== 'undefined')
	output = document.getElementById("output-text");

function printReset() {
	output.value = "";
}
function print(msg) {
	if(output) {
		if(msg)
			output.value += msg;
		output.value += "\n";
	}
	if(msg !== undefined)
		console.log(msg);
	else
		console.log("");
}

// example selection
if(typeof(document) !== 'undefined') {
	function selectSection(selectedValue) {
		printReset();
		for(let el of document.getElementsByClassName("example-section")) {
			if(!el.classList.contains("hidden-section"))
				el.classList.add("hidden-section");
		}
		for(let el of document.getElementsByClassName(selectedValue)) {
			if(el.classList.contains("hidden-section"))
				el.classList.remove("hidden-section");
		}
	}
	
	let selection = document.getElementById("example-selection");
	selection.onchange=function() {
		selectSection(selection.value);
	}
}

export async function togglePassword(ele) {
	for(let el of ele.parentElement.getElementsByClassName("text-password")) {
		if(el.type === "password")
			el.type = "text";
		else
			el.type = "password";
	}
}


window.togglePassword = togglePassword;
window.print = print;
window.printReset = printReset;