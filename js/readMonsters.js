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
			for(var loop = 0; loop < monsters.length; loop++) {
				monsters[loop].feedbackRow = document.createElement("TR");
				monsters[loop].feedbackRow.innerHTML = "<td>" + (loop + 1) + "</td><td>" + monsters[loop].engName + "</td>";
				parseListFeedback.appendChild(monsters[loop].feedbackRow);
			}
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
	//         CR integer or fraction|       |          XPs:    3,400               |       |     |               alignment                   | Size| |Type      |Initiative     |
	//                 |     \1      |       |                \3                    |       v     |                  \4                       | |\5 | |\6|       |\7             |
	var header = /\s+CR (\d+(?:\/\d+)?)\n+XP ((?:(?:\d?\d?\d,){1,3}\d\d\d)|\d?\d?\d?)\n+(?:.+\n+)?(CE\s|CN\s|CG\s|NE\s|N\s|NG\s|LE\s|LN\s|LG\s)(\w+) (.+)\n+Init ([+\-]?\d+);.*\n+/;
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
				if(monsters[loop].mangled) {
					var data = monsters[loop].mangled;
					var string = cell('Regular'); // parse type
					string += cell(data.head[1]); // Challange Ratio
					string += cell(data.head[2]); // XP
					string += cell(data.head[3]); // alignment
			        string += cell(data.head[4]); // size
                    // string += cell(data.head[5]); // "type" example: outsider (native)
                    string += cell(data.head[6]); // initiative
                    string += cell(data.def[1]); // AC
                    string += cell(data.def[2]); // dice count and bonus
                    string += cell('F' + data.def[3] + ' R' + data.def[4] + ' W' + data.def[5]); // save
                    string += cell(data.off[1]); // speed
                    // string += cell(data.off[2]); // flying, in armor, swimming...
                    string += cell(data.stats[1]); // Str
                    string += cell(data.stats[2]); // Dex
                    string += cell(data.stats[3]); // Cos
                    string += cell(data.stats[4]); // Int
                    string += cell(data.stats[5]); // Wis
                    string += cell(data.stats[6]); // Cha
                    monsters[loop].feedbackRow.innerHTML += string;
                }
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
            if(!head) continue;
            var headPos = book.indexOf(head[0], skip);
            if(fixSpaces(name, lineStart(headPos))) {
                skip--;
                skipDiff = 1;
                continue;
            }
            // It's really a match if we reached header by getting no newlines and only whitespace,
            // since the header includes the initial \t, they must simply be contiguous.
            if(where + name.length !== headPos) {
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
    
    // Given a position in the book, go to the character immediately following the previous newline.
    // Identity if book[position] is newline or pos === 0.
    function lineStart(pos) {
        while(pos > 0 && book.charAt(pos) !== '\n') pos--;
        if(pos === 0) return pos;
        return pos + 1;
    }
    
    // Sometimes copypasting makes stupid shit, such as adding spaces to our strings... we cannot allow that so...
    function fixSpaces(name, index) {
        if(index === book.indexOf(name, index)) return false;
        var begin = index;
        var tokens = name.split(/\s+/g);
        for(var loop = 0; loop < tokens.length; loop++) {
            var el = tokens[loop];
            if(book.substr(index, el.length) === el) index += el.length;
            else { // try removing a space
                var got = book.substr(index, el.length + 1).replace(" ", "");
                if(got === el) index += got.length + 1;
                else return false;
            }
            if(loop + 1 < tokens.length) while(index < book.length && book.charAt(index) === ' ') index++;
        }
        // If here we found a match. Replace the string with what I need.
        var conditioned = book.substr(0, begin);
        conditioned += name;
        conditioned += book.substr(index);
        book = conditioned;
        return true;
    }
}

function cell(string) {
    return '<td>' + string + '</td>';
}