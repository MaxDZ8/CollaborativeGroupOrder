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
            document.body.appendChild(realDeal);
            document.body.appendChild(parseListFeedback);
            document.getElementById('realDealInput').onchange = loadFullText;
        };
        reader.readAsText(document.getElementById('listInput').files[0]);
    };
    
    function loadFullText(ev) {
        document.getElementById('realDealInput').disabled = true;
        let reader = new FileReader();
        reader.onload = function() {
            let candidates = partitions(friendlify(reader.result));
            for(let loop = 0; loop < candidates.length; loop++) normalizeNames(candidates[loop]);			
            for(let loop = 0; loop < candidates.length; loop++) {
                candidates[loop].feedbackRow = document.createElement("TR");
                candidates[loop].feedbackRow.innerHTML = "<td>" + (loop + 1) + "</td><td>" + candidates[loop].name + "</td>";
                parseListFeedback.appendChild(candidates[loop].feedbackRow);
            }
			for(let loop = 0; loop < candidates.length; loop++) parseMonster(candidates[loop]);
			
        };
        reader.readAsText(document.getElementById('realDealInput').files[0]);
    }
    
    function normalizeNames(interval) {
        for(let loop = 0; loop < monsters.length; loop++) { // fast accept matching
            if(monsters[loop].engName.toLowerCase() == interval.name.toLowerCase()) return;
        }
        let whitespace = /\s+/g;
        let match = interval.name.toLowerCase();
        for(let loop = 0; loop < monsters.length; loop++) { // fast accept matching
            let reference = monsters[loop].engName.toLowerCase();
            if(match === reference) return; // found and nothing to do.
            if(reference.replace(whitespace, "") !== match.replace(whitespace, "")) continue; // not matching ignoring spaces -> no chance
            let src = 0, dst = 0;
            while(src < reference.length && dst < match.length) {
                if(reference.charAt(src) === match.charAt(dst)) {
                    src++;
                    dst++;
                    continue;
                }
                if(reference.charAt(src) === ' ') break; // spaces must be there!
                if(match.charAt(dst) !== ' ') break; // can ignore extra spaces only
                dst++;
            }
            if(src === reference.length && dst === match.length) {
                interval.name = monsters[loop].engName;
                return;
            }
        }
    }
}


function friendlify(string) {
    return string.replace(/\u2013|\u2014/g, "-").replace(/\r/g, "\n");
}


function partitions(book) {
    // It turns out this header is fairly effective in getting what I need.
    // So, what I do is: I extract all the various headers and everything to the starting newline, which should be monster's name.
    //                                                              Sometimes, an example such as "Aasimar cleric 1"
    //                                                                                      |
    //          CR integer or fraction|      |          XPs:    3,400               |       |     |                              alignment                                      |Size| |Type                             |Initiative     |
    //                  |     \1      |      |                \3                    |       v     |                                 \4                                          ||\5 | |\6                      |        |\7             |
    let header = /\s+CR (\d+(?:\/\d+)?)\n+XP ((?:(?:\d?\d?\d,){1,3}\d\d\d)|\d?\d?\d?)\n+(?:.+\n+)?(CE\s|CN\s|CG\s|NE\s|N\s|NG\s|LE\s|LN\s|LG\s|Any alignment \(same as creator\)\s)(\w+) (.+(?:\s+\([^)]+\))?)\n+Init ([+\-]?\d+);.*\n+/;
    let cand = [];
    let head = book.match(header);
    while(head && head.index < book.length) {
        let lineBeg = lineStart(head.index);
        let found = {
            name: book.substring(lineBeg, head.index),
            header: head,
            body: null
        };
        let headEnd = found.header.index + found.header[0].length;
        while(book[--headEnd] === '\n');
        headEnd++;
        book = book.substr(headEnd);
        head = book.match(header);
        if(!head) {
            found.body = book;
            book = "";
        }
        else {
            lineBeg = lineStart(head.index);
            found.body = book.substr(0, lineBeg);
            book = book.substr(lineBeg);
            head.index -= lineBeg;
        }
        cand.push(found);
    }
    return cand;
    
    // Given a position in the book, go to the character immediately following the previous newline.
    // Identity if book[position] is newline or pos === 0.
    function lineStart(pos) {
        while(pos > 0 && book.charAt(pos) !== '\n') pos--;
        if(pos === 0) return pos;
        return pos + 1;
    }
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
        var tokens = build.engName.split(/\s+/);
        build.engName = "";
        for(var inner = 0; inner < tokens.length; inner++) {
            build.engName += tokens[inner].charAt(0).toLocaleUpperCase();
            build.engName += tokens[inner].substr(1);
            if(inner + 1 < tokens.length) build.engName += ' ';
        }
        if(par) build.subType = par[0];
        if(build.subType) build.subType = build.subType.replace(parAway, "");
        out.push(build);
    }
    return out;
}


function parseMonster(interval) {
	if(!interval || !interval.body) return;
	let mangle = interval.body;
	// With an header and an interval already parsed all we have to do is to the other stuff and comes super easy.
    //                                         |AC |        dice count, type and mod           |          | TS cos        |      | TS dex        |       | TS wis        |
    //                                         |\1 |              |\2                          |          |\3             |      |\4             |       |               |
    const defense = /\n+(?:Defense|DEFENSE)\n+AC (\d+),.+\n+hp \d+ \((\d+d\d+(?:(?:\+|-)(?:\d+))?)\)\n+Fort ((?:\+|-)(?:\d+)), Ref ((?:\+|-)(?:\d+)), Will ((?:\+|-)(?:\d+))\n+/;
    //                                             speed   || extra speed modifiers such as fly, swim 
    //                                            |\1      ||\2|
    const offense = /\n+(?:Offense|OFFENSE)\n+Speed (\d+ ft\.)(.*)\n+/; // this one is very complicated!
    //
    //
    const statistics = /\n+(?:Statistics|STATISTICS)\n+Str (\d+|-),\s+Dex (\d+|-),\s+Con (\d+|-),\s+Int (\d+|-),\s+Wis (\d+|-),\s+Cha (\d+|-)\n+/;
	
	const def = mangle.match(defense);
	if(!def) return;
	mangle = '\n' + mangle.substr(def.index + def[0].length);
	const off = mangle.match(offense);
	if(!off) return;
    mangle = '\n' + mangle.substr(off.index + off[0].length);
    const stats = mangle.match(statistics);
    if(!stats) return;
    let parsed = "";
    parsed = cell('Regular'); // parse type
    parsed += cell(interval.header[1]); // Challange Ratio
    parsed += cell(interval.header[2]); // XP
    parsed += cell(interval.header[3]); // alignment
    parsed += cell(interval.header[4]); // size
    // parsed += cell(interval.header[5]); // "type" example: outsider (native)
    parsed += cell(interval.header[6]); // initiative
    parsed += cell(def[1]); // AC
    parsed += cell(def[2]); // dice count and bonus
    parsed += cell('F' + def[3] + ' R' + def[4] + ' W' + def[5]); // save
    parsed += cell(off[1]); // speed
    // parsed += cell(off[2]); // flying, in armor, swimming...
    parsed += cell(stats[1]); // Str
    parsed += cell(stats[2]); // Dex
    parsed += cell(stats[3]); // Cos
    parsed += cell(stats[4]); // Int
    parsed += cell(stats[5]); // Wis
    parsed += cell(stats[6]); // Cha
    interval.feedbackRow.innerHTML += parsed;
}

function cell(string) {
    return '<td>' + string + '</td>';
}

