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
    //          CR integer or fraction|      |          XPs:    3,400               |       |     |                              alignment                                        |Size| |Type                        |Initiative 
    //                  |     \1      |      |                \3                    |       v     |                                 \4                                            ||\5 | |\6                 |        |\7         
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
    if(!interval || !interval.body || !interval.header) return;
    let parsed = "";
    parsed = cell('Regular'); // parse type
    parsed += cell(interval.header[1]); // Challange Ratio
    parsed += cell(interval.header[2]); // XP
    parsed += cell(interval.header[3]); // alignment
    parsed += cell(interval.header[4]); // size
    // parsed += cell(interval.header[5]); // "type" example: outsider (native)
    parsed += cell(interval.header[6]); // initiative
    interval.feedbackRow.innerHTML += parsed;
    
    let scan = 0;
	{
		scan = findInsensitive("\nDefense") + "\nDefense".length;
		let def = {};
		eatNewlines();
		if(!matchInsensitive("AC "))
			return;
		if(get(scan) > '9' || get(scan) < '0')
			return;
		let beg = scan;
		def.ac = interval.body.substring(beg, goWhitespace());
		if(def.ac.charAt(def.ac.length - 1) === ',') def.ac = def.ac.substring(0, def.ac.length - 1);
		if(def.ac.match(/\D/))
			return;
		if(get(scan) === ',') scan++;
		eatWhitespaces();
		beg = scan;
		goNewline('(', matchRoundPar);
		def.acNotes = interval.body.substring(beg, scan).trim();
		eatNewlines();
		if(!matchInsensitive("hp "))
			return;
		if(get(scan) > '9' || get(scan) < '0')
			return;
		eatDigits();
		eatWhitespaces();
		if(get(scan) !== '(')
			return;
		beg = scan + 1;
		matchRoundPar();
		def.health = interval.body.substring(beg, scan);
		scan++;
		beg = scan;
		def.healthNotes = interval.body.substring(beg, findInsensitive("\nFort "));
		if(scan >= interval.body.length) return;
		scan += "\nFort ".length;
		eatWhitespaces();
		beg = scan;
		if(get(scan) === '+' || get(scan) === '-') scan++;
		eatDigits();
		def.fort = interval.body.substring(beg, scan);
		if(get(scan) === ',') scan++;
		eatWhitespaces();
		if(!matchInsensitive("Ref "))
			return;
		beg = scan;
		if(get(scan) === '+' || get(scan) === '-') scan++;
		eatDigits();
		def.refl = interval.body.substring(beg, scan);
		if(get(scan) === ',') scan++;
		eatWhitespaces();
		if(!matchInsensitive("Will "))
			return;
		beg = scan;
		if(get(scan) === '+' || get(scan) === '-') scan++;
		eatDigits();
		def.will = interval.body.substring(beg, scan);
		if(get(scan) === ',') scan++;
		eatWhitespaces();
		beg = scan;
		def.extra = interval.body.substring(beg, findInsensitive("\noffense\n"));
		
		parsed = "";
		parsed += cell(def.ac + brApp(def.acNotes)); // AC
		parsed += cell(def.health + brApp(def.healthNotes)); // dice count and bonus
		parsed += cell('F' + def.fort + ' R' + def.refl + ' W' + def.will); // save
		interval.feedbackRow.innerHTML += parsed;
	}
	
	{
		if(!matchInsensitive("\noffense\n")) return;
		findInsensitive("\nSpeed ");
		scan += '\nSpeed '.length;
		if(scan >= interval.body.length) return;
		let speed = [];
		while(parseSpeed(speed));
		findInsensitive("\nStatistics");
		
		parsed = "";
		for(let loop = 0; loop < speed.length; loop++) {
			if(loop !== 0) parsed += '<br>';
			parsed += speed[loop].speed;
			if(speed[loop].action) parsed += ' ' + speed[loop].action;
			if(speed[loop].manouver) parsed += ' (' + speed[loop].manouver + ')';
        }
        parsed = cell(parsed); // speed list and manouvers
        interval.feedbackRow.innerHTML += parsed;
    }
    
    
    
    
    
    
    
    function get(i) { return interval.body[i]; }
    
    function matchRoundPar() {
        let open = get(scan) === '('? 1 : 0;
        if(open) scan++;
        while(scan < interval.body.length && open) {
            if(get(scan) === '(') open++;
            else if(get(scan) === ')') {
                open--;
                if(!open) break;
            }
            scan++;
        }
        return scan;
    }
    
    function matchInsensitive(str, offset) {
        if(offset === undefined) offset = scan;
        let match
        for(match = 0; match < str.length && match + offset < interval.body.length; match++) {
            if(str.charAt(match).toUpperCase() !== interval.body.charAt(offset + match).toUpperCase()) break;
        }
        if(match === str.length) scan = offset + str.length;
        return match === str.length;
    }
    
    function findInsensitive(str) {
        for(let loop = scan; loop < interval.body.length; loop++) {
            if(matchInsensitive(str, loop)) {
                scan = loop;
                break;
            }
        }
        return scan;
    }
    
    function goNewline(c, func) {
        if(c === undefined) while(scan < interval.body.length && get(scan) !== '\n') scan++;
        else {
             while(scan < interval.body.length && get(scan) !== '\n') {
                 if(c !== get(scan)) scan++;
                 else func();
             }
        }
        return scan;
    }
    
    function eatNewlines() {
        while(scan < interval.body.length && get(scan) === '\n') scan++;
        return scan;
    }
    
    function goWhitespace() {
        while(scan < interval.body.length && get(scan) > ' ') scan++;
        return scan;
    }
    
    function eatWhitespaces() {
        while(scan < interval.body.length && get(scan) <= ' ') scan++;
        return scan;
    }
    
    function eatDigits() {
        while(scan < interval.body.length && get(scan) >= '0' && get(scan) <= '9') scan++;
        return scan;
    }
    
    function brApp(str) {
        if(!str) return "";
        return '<br>' + str;
    }
    
    // Returns true if something makes us think there's another speed measurement to take.
    // This simply happens if we match a comma as speeds are comma separated.
    // Due to layout, I cannot just go newline.
    function parseSpeed(array) {
        eatWhitespaces();
        let beg = scan;
        goWhitespace();
        let action = interval.body.substring(beg, scan);
        if(action.replace(/\d/g, "").length === 0) { // this must be a count, retry
            action = null;
            scan = beg;
        }
        else eatWhitespaces();
        beg = scan;
        let measure = interval.body.substring(beg, goWhitespace());
        if(measure.replace(/\d/g, "").length !== 0) return false; // NOPE, this must be a number!
        eatWhitespaces();
        if(!matchInsensitive("ft.")) return false; // NOPE, this is always there!
        eatWhitespaces();
        let manouver = null;
        if(get(scan) === '(') {
            beg = scan + 1;
            manouver = interval.body.substring(beg, matchRoundPar());
            scan++;
        }
        let another = get(scan) === ',';
        if(another) scan++;
        let parsed = {
            speed: measure
        };
        if(action) parsed.action = action;
        if(manouver) parsed.manouver = manouver;
        array.push(parsed);
        return another;
    }
}

function cell(string) {
    return '<td>' + string + '</td>';
}

