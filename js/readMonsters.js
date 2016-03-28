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
                el.nameTagCell.innerHTML = '<strong>' + el.headInfo.name[0] + '</strong>' + alternateNames(el.headInfo.name);
                el.feedbackRow.innerHTML = "<td>" + (loop + 1) + "</td>";
                el.feedbackRow.appendChild(el.nameTagCell);
                parseListFeedback.appendChild(el.feedbackRow);
            }
            document.body.appendChild(realDealFeedback);
            for(let loop = 0; loop < candidates.length; loop++) {
                new MonsterParser(candidates[loop]).parse();
                understandMonster(candidates[loop]);
                feedbackMonster(candidates[loop]);
            }
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
        for(let outer = 0; outer < interval.headInfo.name.length; outer++) { 
            let titolised = "";
            let upper = true;
            for(let loop = 0; loop < interval.headInfo.name[outer].length; loop++) {
                let c = interval.headInfo.name[outer].charAt(loop);
                titolised += upper? c.toUpperCase() : c.toLowerCase();
                upper = c <= ' ';
            }
            interval.headInfo.name[outer] = titolised;
            for(let loop = 0; loop < monsters.length; loop++) { // fast accept matching
                if(monsters[loop].engName.toLowerCase() == interval.headInfo.name[outer].toLowerCase()) return;
            }
            let whitespace = /\s+/g;
            let match = interval.headInfo.name[outer].toLowerCase();
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
                    interval.headInfo.name[outer] = monsters[loop].engName;
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
    let head = new HeaderParser(book).match();
    while(head) {
        let found = {
            headInfo: mangleName(head),
            body: null
        };
        const input = head.remaining;
        head = new HeaderParser(head.remaining).match();
        if(!head) found.body = input;
        else found.body = input.substr(0, head.index);
        cand.push(found);
    }
    return cand;

    function mangleName(header) {
        let par = header.name.match(/\s+\([^)]*\)/);
        if(!par || par[0].length === 0) header.name = [ header.name ];
        else {
            let list = [ header.name.substring(0, par.index) ];
            par[0] = par[0].trim();
            let inside = par[0].substr(0, par[0].length - 1).substr(1).trim();
            if(inside.match(/(?:hybrid|human) form/i)) {
                list[0] = header.name;
                header.name = list;
                return header;
            }
            inside = inside.split(/,/g);
            for(let loop = 0; loop < inside.length; loop++) {
                let token = inside[loop].trim();
                if(token && token.length) list.push(token);
            }
            header.name = list;
        }
        return header;
    }
}


function HeaderParser(book) {
    let res = new Parser(book);
    res.match = function() {
        let got = book.match(/\s+CR (\d+(?:\/\d+)?)\n/);
        if(!got) return null;
        let start = lineStart(got.index);
        const headerStart = start;
        const tempName = book.substring(start, got.index).trim();
        const tempCR = got[1];
        res.scan = got.index + got[0].length;
        res.eatNewlines();
        let xp;
        if(res.matchInsensitive('XP ')) { // optional, can be inferred from CR
            res.eatWhitespaces();
            let digits = '';
            let c, separator = false;
            while(c = res.get()) {
                if(c >= '0' && c <= '9') {
                    digits += c;
                    res.scan++;
                    separator = false;
                }
                else if(c === '.' || c === ',') {
                    if(separator) return null;
                    separator = true;
                    res.scan++;
                }
                else if(c === ' ' || c === '\t' || c === '\n') break;
                else return null;
            }
            xp = digits;
            this.eatWhitespaces();
            this.matchInsensitive('each');
        }
        start = res.eatNewlines();
        let leveledCreature;
        const lastConsumed = res.scan;
        // Matching the alignment is quite complicated because it comes with plenty of (rare) modifications
        start = res.scan;
        got = this.findNearestInsensitiveWord([ 'Fine', 'Diminutive', 'Tiny', 'Small',
                                                'Medium', 'Large', 'Huge', 'Gargantuan', 'Colossal' ]);
        if(!got) return null;
        start = lineStart(got.index);
        const tempAlignment = book.substring(start, got.index).trim();
        const tempSize = got.matched;
        leveledCreature = book.substring(lastConsumed, start).trim();
        if(leveledCreature.length === 0) leveledCreature = undefined;
        res.eatWhitespaces();
        const tempType = matchMonsterType();
        if(!tempType) return null;
        res.eatWhitespaces();
        let tempTags;
        if(res.get() === '(') {
            start = res.scan + 1;
            tempTags = book.substring(start, res.matchRoundPar());
            res.scan++;
        }
        res.eatWhitespaces();
        res.eatNewlines();
        if(!res.matchInsensitive("Init")) {
            if(!res.matchInsensitive("Int")) return null;
        }
        res.eatWhitespaces();
        start = res.scan;
        if(res.get() === '+' || res.get() === '-') res.scan++;
        const tempInitiative = book.substring(start, res.eatDigits());
        res.eatWhitespaces();
        let tempSpecialInit;
        if(res.get() === '(') {
            start = res.scan + 1;
            tempSpecialInit = book.substring(start, res.matchRoundPar());
            res.scan++;
            res.eatWhitespaces();
        }
        if(res.get() === ',' || res.get() === ';') res.scan++;
        res.eatWhitespaces();
        start = res.scan;
        const headerEnd = res.findInsensitive("\nDefense\n");
        if(headerEnd >= book.length) return null;
        let tempNotes = book.substring(start, headerEnd);
        
        return mangleAlignment({
            name: tempName,
            cr: tempCR,
            alignment: tempAlignment,
            size: tempSize,
            type: tempType,
            init: tempInitiative,
            
            experience: xp,
            example: leveledCreature,
            tags: mangleTags(tempTags),
            specialInitiative: tempSpecialInit,
            extraNotes: tempNotes,
            
            index: headerStart,
            remaining: book.substring(headerEnd - 1)
        });
    };
    return res;
    
    
    function matchMonsterType() {
        for(let key in monsterType) {
            if(res.matchInsensitive(key)) return key;
        }
        return null;
    }
    
    // Given a position in the book, go to the character immediately following the previous newline.
    // Identity if book[position] is newline or pos === 0.
    function lineStart(pos) {
        while(pos > 0 && book.charAt(pos) !== '\n') pos--;
        if(pos === 0) return pos;
        return pos + 1;
    }
    
    function mangleAlignment(headInfo) {
        const al = headInfo.alignment.toUpperCase();
        const single = {
            'CG': true,    'CN': true,    'CE': true,
            'NE': true,     'N': true,    'NG': true,
            'LG': true,    'LN': true,    'LE': true
        };
        if(single[al]) return headInfo;
        const asCreator = headInfo.alignment.match(/\(same as creator\)/i);
        if(asCreator) {
            headInfo.alignment = headInfo.alignment.replace(asCreator[0], "").trim();
            mangleAlignment(headInfo);
            headInfo.alignment.push('$as_creator');
            return headInfo;
        }
        const any = headInfo.alignment.match(/Any alignment/i);
        if(any) {
            headInfo.alignment = [ '$any' ];
            return headInfo;
        }
        let good = [];
        let parts = headInfo.alignment.split(/\sor\s|,| /gi);
        for(let check = 0; check < parts.length; check++) {
            const str = parts[check].trim().toUpperCase();
            if(!single[str]) {
                alert(headInfo.name + ': unrecognized alignment ' + str + ', ignored.');
                continue;
            }
            good.push(str);
        }
        headInfo.alignment = good;
        return headInfo;
    }

    function mangleTags(tagString) {
        if(!tagString) return [];
        let split = tagString.split(',');
        let result = [];
        for(let loop = 0; loop < split.length; loop++) {
            let nice = split[loop].trim();
            if(nice.length) result.push(nice);
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


function MonsterParser(interval) {
    let res = new Parser(interval.body);
    res.parse = function() {
        if(!interval || !interval.body || !interval.headInfo) return;

        let partition = {
            defense: this.findInsensitive("\nDefense\n"),
            offense: this.findInsensitive("\noffense\n"),
            statistics: this.findInsensitive('\nStatistics\n')
        };
        partition.ordered = partition.defense < partition.offense && partition.offense < partition.statistics;
        if(!partition.ordered) return;
        {
            this.scan = partition.defense + "\nDefense".length;
            let def = {};
            this.eatNewlines();
            if(!this.matchInsensitive("AC "))
                return;
            if(this.get() > '9' || this.get() < '0')
                return;
            let beg = this.scan;
            def.ac = interval.body.substring(beg, this.goWhitespace());
            if(def.ac.match(/[,;]$/)) def.ac = def.ac.substring(0, def.ac.length - 1);
            if(def.ac.match(/\D/))
                return;
            if(this.get() === ',') this.scan++;
            this.eatWhitespaces();
            beg = this.scan;
            if(this.findInsensitive("\nhp ") >= interval.body.length)
                return;
            def.acNotes = interval.body.substring(beg, this.scan).trim();
            this.scan += "\nhp ".length;
            if(this.get() > '9' || this.get() < '0')
                return;
            this.eatDigits();
            this.eatWhitespaces();
            if(this.matchInsensitive('each ')) this.eatWhitespaces();
            if(this.get() !== '(')
                return;
            beg = this.scan + 1;
            this.matchRoundPar();
            def.health = interval.body.substring(beg, this.scan);
            this.scan++;
            beg = this.scan;
            def.healthNotes = interval.body.substring(beg, this.findInsensitive("\nFort "));
            if(this.scan >= interval.body.length) return;
            this.scan++;
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
            this.scan = partition.offense;
            if(!this.matchInsensitive("\noffense\n")) return;
            this.findInsensitive("\nSpeed ", partition.statistics);
            if(!this.matchInsensitive("\nSpeed ")) {
                this.findInsensitive('\nSpd ', partition.statistics);
                if(!this.matchInsensitive('\nSpd ')) return;
            }
            let mangledSpeed = [];
            while(parseSpeed(mangledSpeed));
            if(mangledSpeed.length === 0) return;

            interval.offense = {
                speed: mangledSpeed
            };
        }
        {
            this.scan = partition.statistics;
            if(!this.matchInsensitive('\nStatistics\n')) return;
            this.eatNewlines();
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
    };
    return res;
    
    // Returns true if something makes us think there's another speed measurement to take.
    // This simply happens if we match a comma as speeds are comma separated.
    // Due to layout, I cannot just go newline.
    function parseSpeed(array) {
        res.eatWhitespaces();
        let measure, action;
        while(!measure) {
            let beg = res.scan;
            res.goWhitespace();
            let word = interval.body.substring(beg, res.scan);
            if(word.replace(/\d/g, "").length === 0)  measure = word;
            else {
                action = action? (action + ' ') : "";
                action += word;
            }
            res.eatWhitespaces();
        }
        let beg = res.scan;
        if(!res.matchInsensitive("ft.")) {  // NOPE, this is always there!
            array.length = 0;
            return false;
        }
        res.eatWhitespaces();
        let manouver = null;
        if(res.get() === '(') {
            beg = res.scan + 1;
            manouver = interval.body.substring(beg, res.matchRoundPar());
            res.scan++;
        }
        let another = res.get() === ',';
        if(another) res.scan++;
        let parsed = {
            speed: measure
        };
        if(action) parsed.action = action;
        if(manouver) parsed.manouver = manouver;
        array.push(parsed);
        return another;
    }

    function parseSavingThrow(name) {
        res.eatWhitespaces();
        if(!res.matchInsensitive(name)) return null;
        res.eatWhitespaces();
        let beg = res.scan;
        if(res.get() === '+' || res.get() === '-') res.scan++;
        res.eatDigits();
        let result = {};
        result.main = interval.body.substring(beg, res.scan);
        let back = res.scan;
        res.eatWhitespaces();
        while(res.get() === '(') {
            beg = res.scan + 1;
            res.matchRoundPar();
            if(res.get() === ')') {
                if(!result.special) result.special = [];
                result.special.push(interval.body.substring(beg, res.scan));
                res.scan++;
            }
            back = res.scan;
            res.eatWhitespaces();
            if(res.get() === ',' || res.get() === ';') {
                res.scan++;
                back = res.scan;
            }
            res.eatWhitespaces();
        }
        if(res.get() === ',' || res.get() === ';') {
            res.scan++;
            back = res.scan;
        }
        res.scan = back;
        return result;
    }

    function parseCharacteristics() {
        let chr = [];
        let key  = ['Str', 'Dex', 'Con', 'Int',    'Wis', 'Cha'];
        let dst  = ['str', 'dex', 'con', 'intell', 'wis', 'cha'];
        for(let loop = 0; loop < key.length; loop++) {
            if(!res.matchInsensitive(key[loop])) {
                if(loop === 0) {
                    if(res.matchInsensitive('Abilities ')) {
                        res.eatWhitespaces();
                        loop--;
                        continue;
                    }
                }
                return null;
            }
            if(res.get() > ' ') return null;
            res.eatWhitespaces();
            let beg = res.scan;
            while(res.scan < interval.body.length) {
                let c = res.get();
                if(c < '0' || c > '9') {
                    if(c !== '-') break;
                }
                res.scan++;
            }
            res.eatWhitespaces();
            let build = {
                key: dst[loop],
                value: interval.body.substring(beg, res.scan).trim()
            };
            chr.push(build);
            if(res.get() === ',') res.scan++;
            res.eatWhitespaces();
        }
        return chr;
    }
}


function Parser(body) {
    return {
        scan: 0,
        get: function(i) { return body[i === undefined? this.scan : i]; },

        matchRoundPar: function() {
            let open = this.get(this.scan) === '('? 1 : 0;
            if(open) this.scan++;
            while(this.scan < body.length && open) {
                if(this.get(this.scan) === '(') open++;
                else if(this.get(this.scan) === ')') {
                    open--;
                    if(!open) break;
                }
                this.scan++;
            }
            return this.scan;
        },

        matchInsensitive: function(str, offset) {
            if(offset === undefined) offset = this.scan;
            let match
            for(match = 0; match < str.length && match + offset < body.length; match++) {
                if(str.charAt(match).toUpperCase() !== body.charAt(offset + match).toUpperCase()) break;
            }
            if(match === str.length) this.scan = offset + str.length;
            return match === str.length;
        },

        findInsensitive: function(str, limit) {
            if(limit === undefined) limit = body.length;
            for(let loop = this.scan; loop < limit; loop++) {
                if(this.matchInsensitive(str, loop)) {
                    this.scan = loop;
                    break;
                }
            }
            return this.scan;
        },

        goNewline: function(c, func) {
            if(c === undefined) while(this.scan < body.length && this.get(this.scan) !== '\n') this.scan++;
            else {
                 while(this.scan < body.length && this.get(this.scan) !== '\n') {
                     if(c !== this.get(this.scan)) this.scan++;
                     else func();
                 }
            }
            return this.scan;
        },

        eatNewlines: function() {
            while(this.scan < body.length && this.get(this.scan) === '\n') this.scan++;
            return this.scan;
        },

        goWhitespace: function() {
            while(this.scan < body.length && this.get(this.scan) > ' ') this.scan++;
            return this.scan;
        },

        eatWhitespaces: function() {
            while(this.scan < body.length && this.get(this.scan) <= ' ') this.scan++;
            return this.scan;
        },

        eatDigits: function() {
            while(this.scan < body.length && this.get(this.scan) >= '0' && this.get(this.scan) <= '9') this.scan++;
            return this.scan;
        },
        
        findNearestInsensitiveWord: function(wut) {
            if(wut instanceof Array === false) wut = [ wut ];
            for(; this.scan < body.length; this.scan++) {
                let c = this.get();
                if(c !== ' ' && c !== '\t' && c !== '\n') continue;
                while(this.scan < body.length) {
                    c = this.get();
                    if(c !== ' ' && c !== '\t' && c !== '\n') break;
                    this.scan++;
                }
                if(this.scan >= body.length) return;
                let check, word;
                const start = this.scan;
                for(check = 0; check < wut.length; check++) {
                    word = wut[check];
                    if(this.matchInsensitive(wut[check])) break;
                }
                if(check === wut.length) continue;
                c = this.get();
                if(c !== ' ' && c !== '\t' && c !== '\n') continue;
                this.scan++;
                return {
                    index: start,
                    matched: word
                };
            }
            return null;
        }
    };
}


const monsterType = {
    "aberration": 8,
    "animal": 8,
    "construct": 10,
    "dragon": 12,
    "elemental": 8,
    "fey": 6,
    "giant": 8,
    "humanoid": 8,
    "magical beast": 10,
    "monstrous humanoid": 8,
    "ooze": 10,
    "outsider": 8,
    "plant": 8,
    "undead": 12,
    "vermin": 8
};


function understandMonster(interval) {
    /** Map from type to string to hit dice, it could really be everything. */
    if(!monsterType[interval.headInfo.type.toLowerCase()]) {
        if(!interval.errors) interval.errors = [];
        interval.errors.push('Unknown type "' + interval.headInfo.type + '"');
    }
    const subType = [
        "air",
        "angel",
        "aquatic",
        "archon",
        // Augmented // Special, always comes in the form of Augmented <ORIGINAL_TYPE>
        "chaotic",
        "cold",
        "earth",
        "evil",
        "extraplanar",
        "fire",
        "goblinoid",
        "good",
        "incorporeal",
        "lawful",
        "native",
        "reptilian",
        "shapechanger",
        "swarm",
        "water"
    ];
    const race = {
        'azata': true,
        'boggard': true,
        'giant': true,
        'dark folk': true,
        'demon': true,
        'derro': true,
        'devil': true,
        'elf': true,
        'dwarf': true,
        'elemental': true,
        'kyton': true,
        'human': true,
        'gnome': true,
        'tengu': true,
        'gnoll': true,
        'oni': true
    };
    const discardWithWarn = {
        'varies': 'This tag implies monster changes according to some conditions I cannot understand. You probably want to fix this yourself. Tag ignored.'
    };
    if(interval.headInfo.tags) {
        for(let loop = 0; loop < interval.headInfo.tags.length; loop++) {
            let tag = interval.headInfo.tags[loop];
            if(race[tag.toLowerCase()]) {
                interval.headInfo.race = tag.toLowerCase();
                interval.headInfo.tags = removeByIndex(interval.headInfo.tags, loop);
                loop--;
                continue;
            }
            if(discardWithWarn[tag.toLowerCase()]) {
                if(!interval.errors) interval.errors = [];
                interval.errors.push('Warning: tag "' + tag + '" found. ' + discardWithWarn[tag.toLowerCase()]);
                interval.headInfo.tags = removeByIndex(interval.headInfo.tags, loop);
                continue;
            }
            let scan;
            for(scan = 0; scan < subType.length; scan++) {
                if(subType[scan] === interval.headInfo.tags[loop].toLowerCase()) {
                    interval.headInfo.tags[loop] = subType[scan];
                    break;
                }
            }
            if(scan === subType.length) {
                let aug = interval.headInfo.tags[loop].match(/^Augmented\s+/i);
                if(!aug) {
                    if(!interval.errors) interval.errors = [];
                    interval.errors.push('Unknown sub type "' + interval.headInfo.tags[loop] + '"');
                    continue;
                }
                let originally = interval.headInfo.tags[loop].substr(aug.index + aug[0].length).trim().toLowerCase();
                if(!monsterType[originally]) {
                    if(!interval.errors) interval.errors = [];
                    interval.errors.push('Creature is said to be an augmented "' + originally + '", but this type is unknown to me.');
                    continue;
                }
                interval.augmenting = originally;
                interval.headInfo.tags = removeByIndex(interval.headInfo.tags, loop);
                loop--;
            }
        }
    }
    
    function removeByIndex(arr, goner) {
        let shorter = [];
        for(let cp = 0; cp < goner; cp++) shorter.push(arr[cp]);
        for(let cp = goner + 1; cp < arr.length; cp++) shorter.push(arr[cp]);
        return shorter;
    }
}


function feedbackMonster(interval) {
    if(!interval || !interval.headInfo) return;
    let parsed;
    if(interval.errors && interval.errors.length) {
        let dst = document.getElementById('errors');
        parsed = '<strong>' + interval.headInfo.name[0] + '</strong><ul>';
        for(let loop = 0; loop < interval.errors.length; loop++) {
            parsed += '<li>' + interval.errors[loop] + '</li>';
        }
        parsed += '</ul>';
        dst.innerHTML += parsed;
    }
    {
        if(interval.headInfo.race) interval.nameTagCell.innerHTML = interval.headInfo.race + ', ' + interval.nameTagCell.innerHTML;
        parsed = cell('Basic'); // parse type
        parsed += cell(interval.headInfo.cr); // Challange Ratio
        parsed += cell(interval.headInfo.experience || '<em>inferred</em>'); // XP
        parsed += cell(interval.headInfo.alignment + brApp(interval.headInfo.alignNotes)); // alignment
        parsed += cell(interval.headInfo.size); // size
        interval.nameTagCell.innerHTML += '<br>' + interval.headInfo.type + listSubTypes(interval.headInfo.tags) + (interval.augmenting? ', augmenting: ' + interval.augmenting : ''); // "type" example: outsider (native)
        let init = interval.headInfo.init;
        if(interval.headInfo.specialInitiative) {
            init += '<br><abbr title="' + attributeString(interval.headInfo.specialInitiative) + '">[1]</abbr>';
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
