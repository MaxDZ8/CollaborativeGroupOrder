
// Just copypaste this in the console and I have a quick and effective monster data extraction.
// Parses d20pfsrd monster page.

function extractMonsterData() {
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
    
    const titleTable = getTitle();
    if(!titleTable) {
        alert('Parse failed to match title table.');
        return;
    }
    const headerInfo = nextSiblingBeing(titleTable.node, 'P');
    if(!headerInfo) {
        alert('Parse failed to match header info.');
        return;
    }
    const mob = {
        head: parseHeader(titleTable, headerInfo)
    };
    const clickme = document.createElement('A');
    clickme.href = URL.createObjectURL(new Blob([JSON.stringify(mob, null, 4)], { type: 'text/text' }));
    clickme.innerHTML = "Click me to save results (again).";
    clickme.download = mob.head.name[0] + '.json';
    document.body.appendChild(clickme);
    clickme.click();
    
    
    function getTitle(root) {
        if(undefined === root) {
            const all = document.getElementsByTagName('TABLE');
            for(let loop = 0; loop < all.length; loop++) {
                const matched = getTitle(all[loop]);
                if(matched) return matched;
            }
            return null;
        }
        if(root.tagName !== 'TABLE') return null;
        if(root.childNodes.length !== 3) return null;
        if(root.childNodes[1].tagName !== 'TBODY') return null;
        const tbody = root.childNodes[1];
        if(tbody.childNodes.length !== 3) return null;
        if(tbody.childNodes[1].tagName !== 'TR') return null;
        const tr = tbody.childNodes[1];
        if(tr.childNodes.length !== 5) return null;
        const name = tr.childNodes[1];
        const cr = tr.childNodes[3];
        if(name.tagName !== cr.tagName || cr.tagName !== 'TH') return null;
        const challangeRatio = cr.innerText.match(/^CR\s+(\d+|(?:1\/\d))$/i);
        if(!challangeRatio) return null;
        return {
            node: root,
            name: mangleName(name.innerText),
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
    
    
    function parseHeader(title, overview) {
        let str = overview.innerHTML.replace(/<br>/g, '').replace(/<\/?b>/g, "").replace(/<\/a>/g, "").replace(/<a href="[^"]+">/g, "").trim().split('\n');
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
}
