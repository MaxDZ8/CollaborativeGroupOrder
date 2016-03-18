"use strict";

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
			monsters = parseMonsterList(friendlify(reader.result));
			document.getElementById('listInput').disabled = true;
			var inner = "";
			for(var loop = 0; loop < monsters.length; loop++) inner += "<tr><td>" + (loop + 1) + "</td><td>" + monsters[loop].engName + "</td></tr>";
			parseListFeedback.innerHTML = inner;
			document.body.appendChild(parseListFeedback);
			document.body.appendChild(realDeal);
			document.getElementById('realDealInput').onchange = loadFullText;
		};
		reader.readAsText(document.getElementById('listInput').files[0]);
	};
	
	function loadFullText(ev) {
		var reader = new FileReader();
		reader.onload = function() {
			parseBestiary(monsters, friendlify(reader.result));
		};
		reader.readAsText(document.getElementById('realDealInput').files[0]);
	}
}


function friendlify(string) {
	return string.replace(/\u2013|\u2014/g, "-").replace(/\r/g, "\n");
}


function parseMonsterList(mobs) {
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

function parseBestiary(monsters, book) {
	/* TODO: add "qualifiers" there! They can be for example:
	- Giant - not a size!
	- Young, adult - see dragons
	- ?
	We parse increasingly more difficult stuff.
	Match index name, then fire up the regexps. If the regexp'd blocks are contiguous (only separated by whitespace) then
	we have a global match. */
	//                             Sometimes, an example such as "Aasimar cleric 1"
	//         CR integer or fraction|      |XPs|       |     |               alignment                   | Size| |Type      |Initiative     |
	//                 |     \1      |      | \3|       v     |                  \4                       | |\5 | |\6|       |\7             |
	var header = /\tCR (\d+(?:\/\d+)?)\n+XP (\d+)\n+(?:.+\n+)?(CE\s|CN\s|CG\s|NE\s|N\s|NG\s|LE\s|LN\s|LG\s)(\w+) (.+)\n+Init ([+\-]?\d+);.*\n+/;
	//                                         |AC |        dice count, type and mod           |          | TS cos        |      | TS dex        |       | TS wis        |
	//                                         |\1 |              |\2                          |          |\3             |      |\4             |       |               |
	var defense = /\n+(?:Defense|DEFENSE)\n+AC (\d+),.+\n+hp \d+ \((\d+d\d+(?:(?:\+|-)(?:\d+))?)\)\n+Fort ((?:\+|-)(?:\d+)), Ref ((?:\+|-)(?:\d+)), Will ((?:\+|-)(?:\d+))\n+/;
	//                                             speed   || extra speed modifiers such as fly, swim 
	//                                            |\1      ||\2|
	var offense = /\n+(?:Offense|OFFENSE)\n+Speed (\d+ ft\.)(.*)\n+/; // this one is very complicated!
	//
	//
	var statistics = /\n+(?:Statistics|STATISTICS)\n+Str (\d+|-), Dex (\d+|-), Con (\d+|-), Int (\d+|-), Wis (\d+|-), Cha (\d+|-)\n+/;
	transmogrify();
	
	function transmogrify() {
	    var lenDiff = -1;
	    while(lenDiff !== 0) {
	    	var prevLen = book.length;
	    	for(var loop = 0; loop < monsters.length; loop++) { // simple 'regular' monsters
	    		if(monsters[loop].mangled) continue;
	    		monsters[loop].mangled = matchMonster(monsters[loop].engName, monsters[loop + 1]);
	    	}
	    	lenDiff = book.length - prevLen;
	    }
	}
	
	
	function matchMonster(name, next) {
		var skip = -1;
		var skipDiff = 1;
		while(skipDiff !== 0) {
			skip += skipDiff;
			skipDiff = 0;
			var where = book.indexOf(name, skip);
			if(where < 0) return;
			skipDiff = where - skip + name.length;
			var imbad = book.substr(where);
			var head = imbad.match(header);
			if(!head) {
				continue;
			}
			// It's really a match if we reached header by getting no newlines and only whitespace,
			// since the header includes the initial \t, they must simply be contiguous.
			if(where + name.length !== book.indexOf(head[0], skip)) {
				//skipDiff += head[0].length; // don't skip head here, there are sometimes spurious characters.
				continue;
			}
			head[3] = head[3].trim();
			var def = imbad.match(defense);
			var off = imbad.match(offense);
			if(off) {
				off[2] = off[2].trim();
				if(off[2].length > 2) {
					if(off[2].charAt(0) === '(') off[2] = off[2].substr(1);
					if(off[2].charAt(off[2].length - 1) === ')') off[2] = off[2].substring(0, off[2].length - 1);
				}
			}
			var stats = imbad.match(statistics);
			
			if(next === undefined) next = book.length;
			else next = imbad.indexOf(next.engName) + where;
			blackenParsed(where, next);
			return { head, def, off, stats };
		}
	}
	
	function blackenParsed(start, limit) {
		var prev = book.substring(0, start); // substring index, index. substr index, count
		var parsed = book.substring(start, limit);
		var then = book.substring(limit);
		parsed = parsed.replace(/\n\n/g, "\n").replace(/./g, ""); // "\u2588" <- blacken out
		book = prev + parsed + then;
	}
}
