(function /*extractMonsterData*/() {
    "use strict";
    
    const races = {
        // Core
        'dwarf'    : true,
        'elf'      : true,
        'gnome'    : true,
        'half-orc' : true,
        'halfling' : true,
        'human'    : true,
        // Featured
        'aasimar'   : true,
        'catfolk'   : true,
        'dhampir'   : true,
        'drow'      : true,
        'fetchling' : true,
        'goblin'    : true,
        'hobgoblin' : true,
        'ifrit'     : true,
        'kobold'    : true,
        'orc'       : true,
        'oread'     : true,
        'ratfolk'   : true,
        'sylph'     : true,
        'tengu'     : true,
        'tiefling'  : true,
        'undine'    : true,
        // Standard
        'gnoll'         : true,
        'lizardfolk'    : true,
        'monkey goblin' : true,
        'skinwalker'    : true,
        'triaxian'      : true,
        // Advanced 
        'android'  : true,
        'gathlain' : true,
        'ghoran'   : true,
        'kasatha'  : true,
        'lashunta' : true,
        'shabti'   : true,
        'syrinx'   : true,
        'wyrwood'  : true,
        'wyvaran'  : true,
        // Monstrous
        'centaur' : true,
        'ogre'    : true,
        'shobhad' : true,
        'trox'    : true,
        // Very powerful
        'drider'   : true,
        'gargoyle' : true,
        // uncommon
        'changeling' : true,
        'duergar'     : true,
        'gillmen'     : true,
        'grippli'     : true,
        'kitsune'     : true,
        'merfolk'     : true,
        'nagaji'      : true,
        'samsaran'   : true,
        'strix'       : true,
        'suli'        : true,
        'svirfneblin' : true,
        'vanara'      : true,
        'vishkanya'   : true,
        'wayang'     : true,
        // unknown race points
        'aquatic elf'     : true,
        'astomoi'         : true,
        'caligni'         : true,
        'deep one hybrid' : true,
        'ganzi'           : true,
        'kuru'            : true,
        'munavri'         : true,
        'orang-pendak'    : true,
        'reptoid'         : true
        // other
        // Nothing here?
    };
    
    const monType = {
        'aberration': true,
        'animal': true,
        'construct': true,
        'dragon': true,
        'fey': true,
        'humanoid': true,
        'magical beast': true,
        'monstrous humanoid': true,
        'ooze': true,
        'outsider': true,
        'plant': true,
        'undead': true,
        'vermin': true
    };
    
    const titleTable = getTitle('TH') || getTitle('TD');
    if(!titleTable) {
        alert('Parse failed to match title table.');
        return;
    }
    const mob = {
        head: parseHeader_1(titleTable) ||
              parseHeader_2(titleTable)
    };
    if(!mob.head) {
        alert('Parse failed to match header info.');
        return;
    }
    const clickme = document.createElement('A');
    clickme.href = URL.createObjectURL(new Blob([JSON.stringify(mob, null, 4)], { type: 'text/text' }));
    clickme.innerHTML = "Click me to save results (again).";
    clickme.download = mob.head.name[0] + '.json';
    document.body.appendChild(clickme);
    clickme.click();
    
    
    function getTitle(tagToMatch, el) {
        if(undefined === el) {
            const all = document.getElementsByTagName(tagToMatch);
            for(let loop = 0; loop < all.length; loop++) {
                const matched = getTitle(tagToMatch, all[loop]);
                if(matched) return matched;
            }
            return null;
        }
        if(el.tagName !== tagToMatch) return null;
        const challangeRatio = el.innerText.match(/^CR\s+(\d+|(?:1\/\d))$/i);
        if(!challangeRatio) return null;
        const td = el;
        el = el.parentNode;
        let count = 0, matched = false, first;
        for(let loop = 0; loop < el.childNodes.length; loop++) {
            if(el.childNodes[loop].tagName === tagToMatch) {
                if(first === undefined) first = el.childNodes[loop];
                if(el.childNodes[loop] === td) matched = true;
                count++;
            }
        }
        if(count !== 2 || !matched) return null;
        if(el.parentNode.tagName !== 'TBODY' && el.parentNode.tagName !== 'THEAD') return null;
        el = el.parentNode;
        if(el.parentNode.tagName !== 'TABLE') return null;
        return {
            node: el.parentNode,
            name: mangleName(first.textContent),
            cr: challangeRatio[1]
        };
        
        function mangleName(str) {
            const aka = str.match(/\(([^)])\)/);
            if(!aka) return [ str ];
            return [ str, aka[1] ];
        }
    }
    
    
    function nextSiblingBeing(me, tag) {
        const parent = me.parentNode;
        let scan = 0;
        while(parent.childNodes[scan] !== me) scan++;
        scan++;
        while(scan < parent.childNodes.length) {
            if(parent.childNodes[scan].tagName === tag) return parent.childNodes[scan];
            scan++;
        }
        return null;
    }
    
    
    function parseHeader_1(title) {
        let overview = nextSiblingBeing(title.node, 'P');
        if(!overview) { // variation A
			overview = title.node.parentNode;
			if(overview.tagName !== 'DIV') return null;
			overview = overview.parentNode;
			if(overview.tagName !== 'FONT') return null;
			const div = nextSiblingBeing(overview, 'DIV');
			const p = nextSiblingBeing(overview, 'P');
			if(!div && !p) return null;
			if(div.innerText.trim().toLowerCase().match(/defense(?:s)?/)) overview = p;
			else {
				overview = div.firstElementChild;
				if(overview.tagName !== 'P') return null;
			}
		}
        let str = overview.innerText.trim();
        return parseHeaderParagraph(str, title);
    }
    
    function parseSizeLine(line) {
        const match = line.match(/\s(Fine|Diminutive|Tiny|Small|Medium|Large|Huge|Gargantuan|Colossal)\s/i);
        if(!match) return null;
        let filteredTags;
        let kind;
        const par = line.match(/\s\(([^)]+)\)/);
        if(par) {
            let list = par[1].split(',');
            for(let loop = 0; loop < list.length; loop++) {
                const str = list[loop];
                if(str.length === 0) continue;
                if(races[str.toLowerCase()]) {
                    kind = str.toLowerCase();
                    continue;
                }
                if(filteredTags === undefined) filteredTags = [];
                filteredTags.push(str);
            }
        }
        let matchType = line.substring(match.index + match[0].length, par? par.index : line.length).trim().toLowerCase();
        if(!monType[matchType.toLowerCase()]) {
            alert('Unknown monster type "' + matchType + '", ignored.');
            return null;
        }
        return {
            alignment: line.substring(0, match.index).trim(),
            size: match[1],
            type: matchType.toLowerCase(),
            tags: filteredTags,
            race: kind
        };
    }
    
    function mangleAlignment(alignmentString) {
        const al = alignmentString.toUpperCase();
        const single = {
            'CG': true,    'CN': true,    'CE': true,
            'NE': true,     'N': true,    'NG': true,
            'LG': true,    'LN': true,    'LE': true
        };
        if(single[al]) return [ alignmentString ];
        if(alignmentString.match(/Any(?: alignment)?/i)) return [ '$any' ];
        
        const asCreator = alignmentString.match(/\(same as creator\)/i);
        if(asCreator) {
            alignmentString = alignmentString.replace(asCreator[0], "").trim();
            const rec = mangleAlignment(alignmentString)
            rec.push('$as_creator');
            return rec;
        }
        const always = alignmentString.match(/\s*always\s+/i);
        if(always) {
            alignmentString = alignmentString.replace(always[0], "").trim();
            const rec = mangleAlignment(alignmentString);
            rec.push('$restricted');
            return rec;
        }
        const usually = alignmentString.match(/\s*(?:usually|often)\s+/i); // same as 'most likely', which is the rules say: "...the alignment that the creature is most likely to have...", so just ignore
        if(usually) {
            alignmentString = alignmentString.replace(usually[0], "").trim();
            return mangleAlignment(alignmentString);
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
    
    function parseHeader_2(title) {
        if(title.node.parentNode.tagName !== 'DIV') return null;
        const ignore = title.node.parentNode;
        const cont = ignore.parentNode;
        let header = '';
        for(let loop = 0; loop < cont.childNodes.length; loop++) {
            const el = cont.childNodes[loop];
            if(el === ignore || !el.innerText) continue;
            if(el.innerText.trim().toLowerCase() === 'defense') break;
            header += el.innerText;
        }
        //alert(header);
        return parseHeaderParagraph(header, title);
    }
    
    
    function parseHeaderParagraph(str, title) {
        str = str.split('\n');
        let guess = 0;
        if(str[guess].match(/^XP /)) guess++; // ignore this, CR is sufficient
        let example;
        if(!parseSizeLine(str[guess])) example = str[guess++];
        const szl = parseSizeLine(str[guess++]);
        if(!szl) {
            alert('Size-alignment line expected.');
            return;
        }
        szl.alignment = mangleAlignment(szl.alignment);
        const tmpInit = str[guess].match(/Init ([+-]?\d\d?\d?)[,;]? /i);
        if(!tmpInit) {
            alert('Initiative line expected.');
            return;
        }
        let result = {
            name: title.name,
            cr: title.cr,
            alignment: szl.alignment,
            size: szl.size,
            type: szl.type,
            init: 1 * tmpInit[1]
        };
        if(example) result.example = example;
        if(szl.race) result.race = szl.race;
        if(szl.tags) result.tags = szl.tags;
        return result;
    }
}());
