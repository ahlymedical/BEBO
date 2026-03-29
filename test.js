var srtContent = "Hello\nWorld";
var escapedSRT = srtContent.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\n/g, "\\n").replace(/\r/g, "");
console.log("escapedSRT:", JSON.stringify(escapedSRT));
var script = 'fn("' + escapedSRT + '")';
console.log("script:", script);
