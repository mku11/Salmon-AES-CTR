export function printReset(msg) {}

export function print(msg) {
	if(msg !== undefined)
		console.log(msg);
	else
		console.log("");
}

global.print = print;
global.printReset = printReset;