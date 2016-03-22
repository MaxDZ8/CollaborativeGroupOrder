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
    return string.replace(/\u2013|\u2014/g, "-").replace(/\r/g, "\n").replace(/\nStat istics\n/g, "\nStatistics\n");
}


function partitions(book) {
    // It turns out this header is fairly effective in getting what I need.
    // So, what I do is: I extract all the various headers and everything to the starting newline, which should be monster's name.
    //                                                              Sometimes, an example such as "Aasimar cleric 1"
    //                                                                                      |                                                                                               Sometimes manuals have errors and I cannot just replace this
    //          CR integer or fraction|      |          XPs:    3,400               |       |     |                              alignment                       |  align notes      | Size| |Type                        |       |Initiative 
    //                  |     \1      |      |                \3                    |       v     |                                 \4                           |  \5               | |\6 | |\7                 |        v       |\8     
    let header = /\s+CR (\d+(?:\/\d+)?)\n+XP ((?:(?:\d?\d?\d,){1,3}\d\d\d)|\d?\d?\d?)\n+(?:.+\n+)?(CE\s|CN\s|CG\s|NE\s|N\s|NG\s|LE\s|LN\s|LG\s|Any alignment?\s+)(\([A-Za-z ,;]*\)\s+)?(\w+) (.+(?:\s+\([^)]+\))?)\n+(?:Init|Int) ([+\-]?\d+);.*\n+/;
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
    let list = mobs.split('\n');
	//               name        subtype
	//             | \1       || \2               |
	let pattern = /([A-Za-z ]+)((?:\([A-Za-z ]+\)))?\s+\d+(?:-\d+)?/;
	let out = [];
    for(var loop = 0; loop < list.length; loop++) {
        var el = list[loop];
		if(el === "") continue;
		let match = el.match(pattern);
		if(!match) continue;
		var build = {
			engName: match[1].trim()
		};
        var tokens = build.engName.split(/\s+/);
        build.engName = "";
        for(var inner = 0; inner < tokens.length; inner++) {
            build.engName += tokens[inner].charAt(0).toLocaleUpperCase();
            build.engName += tokens[inner].substr(1);
            if(inner + 1 < tokens.length) build.engName += ' ';
        }
		if(match[2] && match[2].length) build.subType = match[2].substring(1, match[2].length - 1);
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
    parsed += cell(interval.header[3] + brApp(interval.header[4])); // alignment
    parsed += cell(interval.header[5]); // size
    // parsed += cell(interval.header[6]); // "type" example: outsider (native)
    parsed += cell(interval.header[7]); // initiative
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
		if(findInsensitive("\nhp ") >= interval.body.length)
			return;
		def.acNotes = interval.body.substring(beg, scan).trim();
		scan += "\nhp ".length;
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
		scan++;
		def.fort = parseSavingThrow('Fort');
		if(!def.fort) return;
		def.refl = parseSavingThrow('Ref');
		if(!def.refl) return;
		def.will = parseSavingThrow('Will');
		if(!def.will) return;
		def.extra = interval.body.substring(beg, findInsensitive("\noffense\n")).trim();
		
		parsed = "";
		parsed += cell(def.ac + brApp(def.acNotes)); // AC
		parsed += cell(def.health + brApp(def.healthNotes)); // dice count and bonus
		parsed += cell(present('F', def.fort) + present('<br>R', def.refl) + present('<br>W', def.will)); // save
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
    {
        findInsensitive('\nStatistics\n');
        if(!matchInsensitive('\nStatistics\n')) return;
        eatNewlines();
        let chr = parseCharacteristics();
        if(!chr) return;
		
        interval.feedbackRow.innerHTML += cell(chr[0].value) + cell(chr[1].value) + cell(chr[2].value) +
                                          cell(chr[3].value) + cell(chr[4].value) + cell(chr[5].value);
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
	
    function parseSavingThrow(name) {
        eatWhitespaces();
        if(!matchInsensitive(name)) return null;
        eatWhitespaces();
        let beg = scan;
        if(get(scan) === '+' || get(scan) === '-') scan++;
        eatDigits();
        let result = {};
        result.main = interval.body.substring(beg, scan);
		let back = scan;
        eatWhitespaces();
        while(get(scan) === '(') {
            beg = scan + 1;
            matchRoundPar();
            if(get(scan) === ')') {
                if(!result.special) result.special = [];
                result.special.push(interval.body.substring(beg, scan));
                scan++;
            }
			back = scan;
            eatWhitespaces();
            if(get(scan) === ',') {
				scan++;
				back = scan;
			}
            eatWhitespaces();
        }
        if(get(scan) === ',') {
			scan++;
			back = scan;
		}
		scan = back;
        return result;
    }
    
    function parseCharacteristics() {
        let chr = [];
        let key  = ['Str', 'Dex', 'Con', 'Int',    'Wis', 'Cha'];
        let dst  = ['str', 'dex', 'con', 'intell', 'wis', 'cha'];
        for(let loop = 0; loop < key.length; loop++) {
            if(!matchInsensitive(key[loop])) return null;
			if(get(scan) > ' ') return null;
			eatWhitespaces();
            let beg = scan;
            while(scan < interval.body.length) {
                let c = get(scan);
                if(c < '0' || c > '9') {
                    if(c !== '-') break;
                }
                scan++;
            }
            eatWhitespaces();
            let build = {
                key: dst[loop],
                value: interval.body.substring(beg, scan).trim()
            };
            chr.push(build);
            if(get(scan) === ',') scan++;
            eatWhitespaces();
        }
        return chr;
    }

    function present(beg, st) {
        let text = beg + st.main;
        let len = st.special? st.special.length : 0;
        for(let loop = 0; loop < len; loop++) text += '<sub><abbr title="' + attributeString(st.special[loop]) + '">[' + (loop + 1) + ']</abbr></sub>';
        return text;
    }
}

function cell(string) {
    return '<td>' + string + '</td>';
}


function attributeString(string) {
    let out = "";
    for(let loop = 0; loop < string.length; loop++) {
        const c = string.charAt(loop);
        if(c >= '0' && c <= '9') out += c;
        else if(c.toUpperCase() >= 'A' && c.toUpperCase() <= 'Z') out += c;
        else if(c === '-' || c === '_' || c === ' ' || c === '.' || c === '+') out += c;
        else out += '&#' + c.charCodeAt(0) + ';';
    }
    return out;
}
