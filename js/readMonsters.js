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
                const el = candidates[loop];
                el.feedbackRow = document.createElement("TR");
                el.nameTagCell = document.createElement("TD");
                el.nameTagCell.innerHTML = '<strong>' + el.name[0] + '</strong>' + alternateNames(el.name);
                el.feedbackRow.innerHTML = "<td>" + (loop + 1) + "</td>";
                el.feedbackRow.appendChild(el.nameTagCell);
                parseListFeedback.appendChild(el.feedbackRow);
            }
            for(let loop = 0; loop < candidates.length; loop++) {
                parseMonster(candidates[loop]);
                understandMonster(candidates[loop]);
                feedbackMonster(candidates[loop]);
            }
            document.body.appendChild(realDealFeedback);
            document.getElementById('save').onclick = extractUsefulData;
        };
        reader.readAsText(document.getElementById('realDealInput').files[0]);
    }
    
    function alternateNames(nameArray) {
        let str = '';
        for(let loop = 1; loop < nameArray.length; loop++) {
            if(loop === 1) str += '<br>aka ';
            else str += ', ';
            str += nameArray[loop];
        }
        return str;
    }

    function normalizeNames(interval) {
        for(let outer = 0; outer < interval.name.length; outer++) { 
            let titolised = "";
            let upper = true;
            for(let loop = 0; loop < interval.name[outer].length; loop++) {
                let c = interval.name[outer].charAt(loop);
                titolised += upper? c.toUpperCase() : c.toLowerCase();
                upper = c <= ' ';
            }
            interval.name[outer] = titolised;
            for(let loop = 0; loop < monsters.length; loop++) { // fast accept matching
                if(monsters[loop].engName.toLowerCase() == interval.name[outer].toLowerCase()) return;
            }
            let whitespace = /\s+/g;
            let match = interval.name[outer].toLowerCase();
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
                    interval.name[outer] = monsters[loop].engName;
                    break;
                }
            }
        }
    }

    function extractUsefulData() {
        let book = fetch([ 'best1',
                           'best2',
                           'best3',
                           'ap_riseOfTheThunderlords',
                           'ap_curseOfTheCrimsonThrone',
                           'ap_secondDarkness',
                           'ap_legacyOfFire',
                           'ap_councilOfThieves',
                           'ap_kingMaker',
                           'ap_serpentsSkull',
                           'ap_carrionCrown',
                           'ap_jadeRegent' ]);
        if(!book) {
            alert("Select book to save first.");
            return;
        }
    }
    
    function fetch(what) {
        for(let loop = 0; loop < what.length; loop++) {
            let el = document.getElementById(what[loop]);
            if(el && el.checked) return el.value;
        }
        return null;
    }
}


function friendlify(string) {
    return string.replace(/\u2013|\u2014/g, "-").replace(/\r/g, "\n")
        .replace(/\n(?:Stat istics|Statisti cs|Stat ist ics|Statis tics)\n/gi, "\nStatistics\n")
        .replace(/\n(?:Offens e|Off ens e|Offen se)\n/gi, '\nOffense\n')
        .replace(/\n(?:Defens e|De fense|Defe nse|Defenses)\n/gi, '\nDefense\n')
        .replace(/ fl at-footed /gi, " flat-footed ");
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
            name: mangleName(book.substring(lineBeg, head.index)),
            headInfo: mangleType(head),
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
            /\s+CR (\d+(?:\/\d+)?)\n+XP ((?:(?:\d?\d?\d,){1,3}\d\d\d)|\d?\d?\d?)(?: each)?\n+(?:.+\n+)?(CE\s|CN\s|CG\s|NE\s|N\s|NG\s|LE\s|LN\s|LG\s|Any alignment?\s+)(\([A-Za-z ,;]*\)\s+)?(\w+) (.+(?:\s+\([^)]+\))?)\n+(?:Init|Int) ([+\-]?\d+)(\s+\([^)]*\))?[;,].*\n+/,


        // Header used in AP 01-06 Rise of the runelords
        // There are no XPs (I guess it's inferred from CR) but for the rest it's the same.
            /\s+CR (\d+(?:\/\d+)?)\n+(?:.+\n+)?((?:Always |Usually |Often )?(?:CE\s|CN\s|CG\s|NE\s|N\s|NG\s|LE\s|LN\s|LG\s|Any alignment?\s+))(\([A-Za-z ,;]*\)\s+)?(\w+) (.+(?:\s+\([^)]+\))?)\n+(?:Init|Int) ([+\-]?\d+)(\s+\([^)]*\))?[;,].*\n+/
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

    function mangleName(name) {
        let par = name.match(/\s+\([^)]*\)/);
        if(par && par[0]) {
            let list = [ name.substring(0, par.index) ];
            par[0] = par[0].trim();
            let inside = par[0].substr(0, par[0].length - 1).substr(1).trim();
            if(inside.match(/(?:hybrid|human) form/i)) {
                list[0] = name;
                return list;
            }
            inside = inside.split(/,/g);
            for(let loop = 0; loop < inside.length; loop++) {
                let token = inside[loop].trim();
                if(token && token.length) list.push(token);
            }
            return list;
        }
        return [ name ];
    }

    function mangleType(interval) {
        let scan;
        for(scan = 0; scan < interval.type.length; scan++) {
            if(interval.type.charAt(scan) === '(') break;
        }
        if(scan === interval.type.length) return interval; // no subtype, rare, but not impossible
        scan++;
        let level = 1;
        const beg = scan;
        while(scan < interval.type.length) {
            if(interval.type.charAt(scan) === '(') level++;
            else if(interval.type.charAt(scan) === ')') {
                level--;
                if(level === 0) break;
            }
            scan++;
        }
        let par = interval.type.substring(beg, scan).split(',');
        interval.tags = [];
        for(let loop = 0; loop < par.length; loop++) {
            if(par[loop]) par[loop] = par[loop].trim();
            if(par[loop] && par[loop].length) interval.tags.push(par[loop]);
        }
        interval.type = interval.type.substring(0, beg - 1).trim();
        return interval;
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

    let scan = 0;
    let partition = {
        defense: findInsensitive("\nDefense"),
        offense: findInsensitive("\noffense\n"),
        statistics: findInsensitive('\nStatistics\n')
    };
    partition.ordered = partition.defense < partition.offense && partition.offense < partition.statistics;
    if(!partition.ordered) return;
    {
        scan = partition.defense + "\nDefense".length;
        let def = {};
        eatNewlines();
        if(!matchInsensitive("AC "))
            return;
        if(get(scan) > '9' || get(scan) < '0')
            return;
        let beg = scan;
        def.ac = interval.body.substring(beg, goWhitespace());
        if(def.ac.match(/[,;]$/)) def.ac = def.ac.substring(0, def.ac.length - 1);
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
        def.extra = interval.body.substring(beg, partition.offense).trim();
        interval.defense = def;
    }
    {
        scan = partition.offense;
        if(!matchInsensitive("\noffense\n")) return;
        findInsensitive("\nSpeed ", partition.statistics);
        if(!matchInsensitive("\nSpeed ")) {
            findInsensitive('\nSpd ', partition.statistics);
            if(!matchInsensitive('\nSpd ')) return;
        }
        let mangledSpeed = [];
        while(parseSpeed(mangledSpeed));
        if(mangledSpeed.length === 0) return;

        interval.offense = {
            speed: mangledSpeed
        };
    }
    {
        scan = partition.statistics;
        if(!matchInsensitive('\nStatistics\n')) return;
        eatNewlines();
        let chr = parseCharacteristics();
        if(!chr) return;
        interval.statistics = {
            str: chr[0].value,
            dex: chr[1].value,
            cos: chr[2].value,
            intell: chr[3].value,
            wis: chr[4].value,
            cha: chr[5].value
        };
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

    function findInsensitive(str, limit) {
        if(limit === undefined) limit = interval.body.length;
        for(let loop = scan; loop < limit; loop++) {
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

    // Returns true if something makes us think there's another speed measurement to take.
    // This simply happens if we match a comma as speeds are comma separated.
    // Due to layout, I cannot just go newline.
    function parseSpeed(array) {
        eatWhitespaces();
        let measure, action;
        while(!measure) {
            let beg = scan;
            goWhitespace();
            let word = interval.body.substring(beg, scan);
            if(word.replace(/\d/g, "").length === 0)  measure = word;
            else {
                action = action? (action + ' ') : "";
                action += word;
            }
            eatWhitespaces();
        }
        let beg = scan;
        if(!matchInsensitive("ft.")) {  // NOPE, this is always there!
            array.length = 0;
            return false;
        }
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
            if(get(scan) === ',' || get(scan) === ';') {
                scan++;
                back = scan;
            }
            eatWhitespaces();
        }
        if(get(scan) === ',' || get(scan) === ';') {
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
            if(!matchInsensitive(key[loop])) {
                if(loop === 0) {
                    if(matchInsensitive('Abilities ')) {
                        eatWhitespaces();
                        loop--;
                        continue;
                    }
                }
                return null;
            }
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
}


function understandMonster(interval) {
}


function feedbackMonster(interval) {
    if(!interval || !interval.headInfo) return;
    let parsed;
    {
        parsed = cell('Basic'); // parse type
        parsed += cell(interval.headInfo.cr); // Challange Ratio
        parsed += cell(interval.headInfo.experience || '<em>inferred</em>'); // XP
        parsed += cell(interval.headInfo.alignment + brApp(interval.headInfo.alignNotes)); // alignment
        parsed += cell(interval.headInfo.size); // size
        interval.nameTagCell.innerHTML += '<br>' + interval.headInfo.type + listSubTypes(interval.headInfo.tags); // "type" example: outsider (native)
        let init = interval.headInfo.initiative;
        if(interval.headInfo.initSpecials) {
            init += '<br><abbr title="' + attributeString(interval.headInfo.initSpecials) + '">[1]</abbr>';
        }
        parsed += cell(init); // initiative
        interval.feedbackRow.innerHTML += parsed;
    }
    if(!interval.defense) return;
    {
        let def = interval.defense;
        parsed = "";
        parsed += cell(def.ac + brApp(def.acNotes)); // AC
        parsed += cell(def.health + brApp(def.healthNotes)); // dice count and bonus
        parsed += cell(present('F', def.fort) + present('<br>R', def.refl) + present('<br>W', def.will)); // save
        interval.feedbackRow.innerHTML += parsed;
    }
    if(!interval.offense) return;
    {
        let speed = interval.offense.speed;
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
    if(!interval.statistics) return;
    {
        let stat = interval.statistics;
        interval.feedbackRow.innerHTML += cell(stat.str) + cell(stat.dex) + cell(stat.cos) +
                                          cell(stat.intell) + cell(stat.wis) + cell(stat.cha);
    }

    function listSubTypes(array) {
        if(!array) return "";
        let result = '';
        for(let loop = 0; loop < array.length; loop++) {
            result += loop === 0? '<br>' : ', ';
            result += array[loop];
        }
        return result;
    }

    function cell(string) {
        return '<td>' + string + '</td>';
    }

    function brApp(str) {
        if(!str) return "";
        return '<br>' + str;
    }

    function present(beg, st) {
        let text = beg + st.main;
        let len = st.special? st.special.length : 0;
        for(let loop = 0; loop < len; loop++) text += '<sub><abbr title="' + attributeString(st.special[loop]) + '">[' + (loop + 1) + ']</abbr></sub>';
        return text;
    }
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
