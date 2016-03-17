
window.onload = function() {
	var parseListFeedback = document.getElementById('parseListFeedback');
	var realDeal = document.getElementById('realDeal');
	var realDealFeedback = document.getElementById('realDealFeedback');
	parseListFeedback.parentNode.removeChild(parseListFeedback);
	realDeal.parentNode.removeChild(realDeal);
	realDealFeedback.parentNode.removeChild(realDealFeedback);
	var monsters = [];
	document.getElementById('listInput').onchange = function(ev) {
		var reader = new FileReader();
		reader.onload = function() {
			monsters = parseMonsterList(reader.result);
			var start = document.getElementById('start');
			start.parentNode.removeChild(start);
			document.body.appendChild(realDeal);
			document.body.appendChild(parseListFeedback);
			document.getElementById('realDealInput').onchange = loadFullText;
		};
		reader.readAsText(document.getElementById('listInput').files[0]);
	};
	
	function loadFullText(ev) {
		var reader = new FileReader();
		reader.onload = function() {
			alert("cool stuff!");
		};
		reader.readAsText(document.getElementById('realDealInput').files[0]);
	}
}
	
function parseMonsterList(mobs) {
	mobs = mobs.replace(/\u2013/g, "-");
	mobs = mobs.replace(/\r/g, "\n");
	var list = mobs.split(/\s+\d+(?:-\d+)?\n\n/m);
	if(list[list.length - 1] === "") list.length--;
	var out = [];
	var subType = /\(.*\)/;
	var parAway = /\(|\)/g;
	for(var loop = 0; loop < list.length; loop++) {
		var el = list[loop];
		var par = el.match(subType);
		var build = {};
		if(par) el = el.replace(par, "").trim();
		build.engName = el;
		if(par) build.subType = par[0];
		if(build.subType) build.subType = build.subType.replace(parAway, "");
		out.push(build);
	}
	return out;
}
