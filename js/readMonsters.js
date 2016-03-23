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
        let titolised = "";
        let upper = true;
        for(let loop = 0; loop < interval.name.length; loop++) {
            let c = interval.name.charAt(loop);
            titolised += upper? c.toUpperCase() : c.toLowerCase();
            upper = c <= ' ';
        }
        interval.name = titolised;
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
                break;
            }
        }
    }
}


function friendlify(string) {
    return string.replace(/\u2013|\u2014/g, "-").replace(/\r/g, "\n").replace(/\nStat istics\n/g, "\nStatistics\n")
        .replace(/ fl at-footed /g, " flat-footed ");
}


function partitions(book) {
    // It turns out this header is fairly effective in getting what I need.
    // So, what I do is: I extract all the various headers and everything to the starting newline, which should be monster's name.
    let cand = [];
    let headerType;
    let head = matchHeader();
    while(head && head.index < book.length) {
        let lineBeg = lineStart(head.index);
        let found = {
            name: book.substring(lineBeg, head.index),
            headInfo: head,
            body: null
        };
        let headEnd = found.headInfo.index + found.headInfo.length;
        while(book[--headEnd] === '\n');
        headEnd++;
        book = book.substr(headEnd);
        head = matchHeader();
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
    
    function matchHeader() {
        let h = [
        // Header as used in bestiary 1, 2, and 3
        //                                                               Sometimes, an example such as "Aasimar cleric 1"
        //                                                                                       |                                                                                               Sometimes manuals have errors and I cannot just replace this
        // CR integer or fraction|      |          XPs:    3,400               |                 |     |                              alignment                       |  align notes      | Size| |Type                        |       |Initiative| Special initiative
        //         |     \1      |      |                \2                    |                 v     |                                 \3                           |  \4               | |\5 | |\6                 |        v       |\7        | \8
            /\s+CR (\d+(?:\/\d+)?)\n+XP ((?:(?:\d?\d?\d,){1,3}\d\d\d)|\d?\d?\d?)(?: each)?\n+(?:.+\n+)?(CE\s|CN\s|CG\s|NE\s|N\s|NG\s|LE\s|LN\s|LG\s|Any alignment?\s+)(\([A-Za-z ,;]*\)\s+)?(\w+) (.+(?:\s+\([^)]+\))?)\n+(?:Init|Int) ([+\-]?\d+)(\s+\([^)]*\))?;.*\n+/,
            
        
        // Header used in AP 01-06 Rise of the runelords
        // There are no XPs (I guess it's inferred from CR) but for the rest it's the same.
            /\s+CR (\d+(?:\/\d+)?)\n+(?:.+\n+)?((?:Always |Usually )?(?:CE\s|CN\s|CG\s|NE\s|N\s|NG\s|LE\s|LN\s|LG\s|Any alignment?\s+))(\([A-Za-z ,;]*\)\s+)?(\w+) (.+(?:\s+\([^)]+\))?)\n+(?:Init|Int) ([+\-]?\d+)(\s+\([^)]*\))?;.*\n+/
        ];
        let match;
        if(headerType === undefined) { // headers must be coherent!
            match = book.match(h[0]);
            if(match) headerType = 0;
            else {
                match = book.match(h[1]);
                if(match) headerType = 1;
            }
        }
        else match = book.match(h[headerType]);
        if(!match) return;
        let result = {
            index: match.index,
            length: match[0].length,
            
            cr: match[1],
        };
        let next = 2;
        if(headerType === 0) {
            result.experience = match[2];
            next = 3;
        }
        result.alignment = match[next++];
        result.alignNotes = match[next++];
        result.size = match[next++];
        result.type = match[next++];
        result.initiative = match[next++];
        result.initSpecials = match[next++];
        if(result.initSpecials) {
            let src = result.initSpecials.trim();
            result.initSpecials = src.substring(1, src.length - 1);
        }
        return result;
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
    if(!interval || !interval.body || !interval.headInfo) return;
    let parsed = "";
    {
        parsed = cell('Regular'); // parse type
        parsed += cell(interval.headInfo.cr); // Challange Ratio
        parsed += cell(interval.headInfo.experience || '&lt;inferred&gt;'); // XP
        parsed += cell(interval.headInfo.alignment + brApp(interval.headInfo.alignNotes)); // alignment
        parsed += cell(interval.headInfo.size); // size
        // parsed += cell(interval.header[6]); // "type" example: outsider (native)
        let init = interval.headInfo.initiative;
        if(interval.headInfo.initSpecials) {
            init += '<br><abbr title="' + attributeString(interval.headInfo.initSpecials) + '">[1]</abbr>';
        }
        parsed += cell(init); // initiative
        interval.feedbackRow.innerHTML += parsed;
    }
    
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
        if(matchInsensitive('each ')) eatWhitespaces();
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
