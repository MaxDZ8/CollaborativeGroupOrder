"use strict";

const dragonAgeCategory = [
    'wyrmling',        'very young',  'young',
    'juvenile',        'young adult', 'adult',
    'mature adult',    'old',         'very old',
    'ancient',         'wyrm',        'great wyrm'
];

const sizeModifier = [
    'fine',   'diminutive',
    'tiny',   'small',
    'medium', 'large',
    'huge',   'gargantuan',
    'colossal'
];

const wellKnownTemplates = {
    'giant': 'giant',
    'dire': 'dire',
    'petitioner': 'petitioner'
};

window.onload = function() {
    const start = document.getElementById('loader');
    const nameTable = document.getElementById('nameConditioning');
    const errorSpan = countElements('th', nameTable.getElementsByTagName('TR')[0]) - 1;
    const loadedMonsters = []; /* {
        data: <loaded>,
        hints: Nullable<array of span with hint controls,
        td: <control cell, nullable>
        fileName: <where to save/load>
    } */
    start.onchange = function(el) {
        start.disabled = true;
        recursiveLoad(start.files);
    };
    
    function cell(innerHTML, colSpan) {
        let res = '<td' + (colSpan? ' colspan="' + colSpan + '"' : '') + '>';
        res += innerHTML;
        return res + '</td>';
    }
    
    function span(innerHTML) {
        const res = document.createElement('SPAN');
        res.innerHTML = innerHTML;
        return res;
    }
    
    function countElements(tag, container) {
        tag = tag.toUpperCase();
        let count = 0;
        for(let loop = 0; loop < container.children.length; loop++) {
            if(container.children[loop].tagName === tag) count++;
        }
        return count;
    }
    
    function recursiveLoad(files, loadIndex) {
        if(loadIndex === undefined) loadIndex = 0;
        if(loadIndex === files.length) {
            guessGroups();
            return;
        }
        const reader = new FileReader();
        reader.onload = function() {
            recursiveLoad(files, loadIndex + 1);
            const obj = JSON.parse(reader.result);
            const tr = document.createElement('TR');
            tr.innerHTML += cell(loadIndex) + cell(files[loadIndex].name);
            if(!obj.head) {
                tr.innerHTML += cell('Missing "head" field.', errorSpan);
                nameTable.appendChild(tr);
                return;
            }
            let unknown = [];
            for(let key in obj) {
                if(key !== 'head') unknown.push(key);
            }
            if(unknown.length) {
                tr.innerHTML += cell('Contains ' + unknown.length + ' unknown fields.', errorSpan);
                nameTable.appendChild(tr);
                return;
            }
            const persist = {
                data: obj,
                hints: null,
                td: null,
                fileName: files[loadIndex].name
            };
            loadedMonsters.push(persist);
            let nameContent = '';
            for(let loop = 0; loop < obj.head.name.length; loop++) {
                if(loop !== 0) nameContent += '<br/>';
                nameContent += obj.head.name[loop];
            }
            tr.innerHTML += cell(nameContent);
            const td = document.createElement('TD');
            persist.hints = suggestFileAndHeaderHints(files[loadIndex], obj, tr, td);
            if(!persist.hints) return;
            tr.appendChild(td);
            persist.td = td;
            for(let loop = 0; loop < persist.hints.length; loop++) td.appendChild(persist.hints[loop]);
            nameTable.appendChild(tr);
        };
        reader.readAsText(files[loadIndex]);
    }
    
    function suggestFileAndHeaderHints(file, monster, tr, td) {
        const invalidChars = /[^- a-zA-Z0-9'\u2019]/g;
        let note = [];
        for(let loop = 0; loop < monster.head.name.length; loop++) {
            const name = monster.head.name[loop];
            if(name !== name.trim()) {
                note.push(makeButton(file.name, 'trim name[' + loop + '].', monster, ', trimmed name[' + loop + ']', function() {
                    monster.head.name[loop] = monster.head.name[loop].trim();
                }));
                continue;
            }
            const nbspAway = monster.head.name[loop].replace(/\u00a0/g, ' ');
            if(nbspAway !== monster.head.name[loop]) {
                note.push(makeButton(file.name, 'name[' + loop + '].replace(&amp;NBSP, SPACE)', monster, ', name[' + loop + '], replaced &amp;nbsp;', function() {
                    monster.head.name[loop] = nbspAway;
                }));
                continue;
            }
            if(monster.head.name[loop].match(invalidChars)) {
                let parts = monster.head.name[loop].split(',');
                for(let inner = 0; inner < parts.length; inner++) parts[inner] = parts[inner].trim();
                let age;
                let skipIndex;
                for(let inner = 0; inner < parts.length; inner++) {
                    for(let match = 0; match < dragonAgeCategory.length; match++) {
                        if(parts[inner].toLowerCase() === dragonAgeCategory[match]) {
                            age = dragonAgeCategory[match];
                            skipIndex = inner;
                            break;
                        }
                    }
                }
                if(age) {
                    let newName = '';
                    for(let inner = 0; inner < parts.length; inner++) {
                        if(inner === skipIndex) continue;
                        if(newName.length) newName += ', ';
                        newName += parts[inner];
                    }
                    note.push(makeButton(file.name, 'AGE: "' + age + '" to annotation', monster, ', ' + monster.head.name[loop] + ': changed to "' + newName + '" and added annotation [[' + age + ']]', function() {
                        monster.head.name[loop] = newName;
                        if(!monster.head.extraNotes) monster.head.extraNotes = [];
                        monster.head.extraNotes.push({
                            type: 'ageCategory',
                            value: age
                        });
                    }));
                    continue;
                }
                let wkt;
                for(let inner = 0; inner < parts.length; inner++) {
                    if(parts[inner].toLowerCase() in wellKnownTemplates) {
                        wkt = {
                            skip: inner,
                            template: wellKnownTemplates[parts[inner].toLowerCase()]
                        };
                        break;
                    }
                }
                if(wkt) {
                    let newName = '';
                    for(let inner = 0; inner < parts.length; inner++) {
                        if(inner === wkt.skip) continue;
                        if(newName.length) newName += ', ';
                        newName += parts[inner];
                    }
                    note.push(makeButton(file.name, 'TEMPLATE: ' + wkt.template, monster, monster.head.name[loop] + ': changed to "' + newName + '" and added template [[' + wkt.template + ']]', function() {
                        monster.head.name[loop] = newName;
                        if(!monster.head.extraNotes) monster.head.extraNotes = [];
                        monster.head.extraNotes.push({
                            type: 'appliedTemplate',
                            value: wkt.template
                        });
                    }));
                    continue;
                }
                
                const par = matchRoundPar(monster.head.name[loop]);
                if(par) {
                    const ori = monster.head.name[loop];
                    let newName = ori.substring(0, par.open).trim();
                    const trailing = ori.substring(par.next).trim();
                    if(trailing.length) newName += ' ' + trailing;
                    let apply = document.createElement('BUTTON');
                    note.push(apply);
                    
                    if(par.inside.match(/ form$/i)) {
                        const morph = par.inside.substring(0, par.inside.length - 5);
                        note.push(makeButton(file.name, 'MORPH_TARGET: ' + morph, monster, monster.head.name[loop] + ': morph target variation: ' + morph, function() {
                            monster.head.name[loop] = newName;
                            monster.head.extraNotes.push({
                                type: 'variant.morphTarget',
                                value: morph
                            });
                        }));
                        continue;
                    }
                    
                    let size;
                    for(let check = 0; check < sizeModifier.length; check++) {
                        if(sizeModifier[check] === par.inside.toLowerCase()) {
                            size = sizeModifier[check];
                            break;
                        }
                    }
                    if(size) {
                        note.push(makeButton(file.name, 'SIZE: ' + par.inside, monster, monster.head.name[loop] + ': added size specifier: ' + size, function() {
                            monster.head.name[loop] = newName;
                            monster.head.extraNotes.push({
                                type: 'variant.size',
                                value: size
                            });
                        }));
                    }
                    else {
                        note.push(makeButton(file.name, '"' + par.inside + '" alternate name', monster, monster.head.name[loop] + ': extracted an alternate name.', function() {
                            monster.head.name[loop] = newName;
                            monster.head.name.push(titolize(par.inside));
                        }));
                        note.push(makeButton(file.name, 'MISC: ' + par.inside, monster, monster.head.name[loop] + ': extracted misc variation.', function() {
                            monster.head.name[loop] = newName;
                            monster.head.extraNotes.push({
                                type: 'extraInfo',
                                value: par.inside.trim()
                            });
                        }));
                    }
                    continue;
                }
                note.push(span('name[' + loop + '] contains odd chars.'));
                note.push(document.createElement('BR'));
            }
        }
        if(note.length === 0) note = null;
        return note;
    }
    
    function matchRoundPar(string) {
        let start = 0;
        while(start < string.length && string.charAt(start) !== '(') start++;
        if(start >= string.length) return null;
        let scan = start + 1;
        let count = 1;
        while(scan < string.length) {
            if(string.charAt(scan) === '(') count++;
            else if(string.charAt(scan) === ')') {
                count--;
                if(count === 0) break;
            }
            scan++;
        }
        return {
            open: start,
            next: scan + (string.charAt(scan) === ')'? 1 : 0),
            inside: string.substring(start + 1, scan).trim()
        };
    }
    
    function titolize(string) {
        const parts = string.split(' ');
        let nicer = '';
        const avoid = [ 'of', 'the' ];
        for(let loop = 0; loop < parts.length; loop++) {
            const word = parts[loop];
            let uppercasify = true;
            for(let check = 0; check < avoid.length; check++) {
                if(avoid[check] === word.toLowerCase()) {
                    uppercasify = false;
                    break;
                }
            }
            if(loop === 0) uppercasify = true;
            if(nicer.length) nicer += ' ';
            if(uppercasify) nicer += word.charAt(0).toUpperCase() + word.substr(1);
            else nicer += word.toLowerCase();
        }
        return nicer;
    }
    
    function activateModification(monster, apply) {
        let match;
        for(let loop = 0; loop < loadedMonsters.length; loop++) {
            if(loadedMonsters[loop].data === monster) {
                match = loadedMonsters[loop].hints;
                break;
            }
        }
        if(!match) return; // uh?
        for(let loop = 0; loop < match.length; loop++) {
            if(match[loop] === apply) continue;
            match[loop].parentNode.removeChild(match[loop]);
        }
        apply.disabled = true;
    }
    
    function guessGroups() {
        const groups = {};
        for(let loop = 0; loop < loadedMonsters.length; loop++) {
            const parts = loadedMonsters[loop].data.head.name[0].split(',');
            if(parts.length === 1) continue;
            const candidate = parts[0].charAt(0).toUpperCase() + parts[0].substr(1);
            if((candidate in groups) === false) {
                groups[candidate] = {};
                groups[candidate].likely = [];
            }
            groups[candidate].likely.push(loadedMonsters[loop]);            
        }
        // Now, ignore all groups having a single candidate, they're likely being something else.
        // In other cases, discard current annotations if there and replace with a button.
        for(let key in groups) {
            if(groups[key].likely.length === 1) continue;
            for(let loop = 0; loop < groups[key].likely.length; loop++) {
                const el = groups[key].likely[loop];
                if(!el.td) continue; // impossible, ',' comma triggers a 'weird character error for sure'.
                while(el.td.firstChild) el.td.removeChild(el.td.firstChild);
                let button = document.createElement('BUTTON');
                button.innerHTML = 'GROUP: ' + key;
                const monster = el.data;
                const group = key;
                button.onclick = function(ev) {
                    activateModification(monster, ev.currentTarget);
                    const gotcha = document.createElement('A');
                    document.body.appendChild(gotcha);
                    document.body.appendChild(document.createElement('BR'));
                    gotcha.innerHTML = el.fileName + ': to group: ' + group;
                    extractGroup(monster.head.name, group);
                    if(!monster.head.extraNotes) monster.head.extraNotes = [];
                    monster.head.extraNotes.push({
                        type: 'group',
                        value: group
                    });
                    gotcha.href = URL.createObjectURL(new Blob([ JSON.stringify(monster, null, 4) ], { type: "application/json" }));
                    gotcha.download = el.fileName;
                    gotcha.click();
                };
                el.hints = [ button ];
                el.td.appendChild(button);
                for(let inner = 0; inner < monster.head.name.length; inner++) {
                    const comma = monster.head.name[inner].indexOf(',');
                    const start = monster.head.name[inner].substring(0, comma).trim();
                    if(start.toLowerCase() !== group.toLowerCase()) continue;
                    const newName = monster.head.name[inner].substr(comma + 1).trim();
                    button = document.createElement('BUTTON');
                    button.innerHTML = group + '&gt;' + newName;
                    const redefine = inner;
                    button.onclick = function(ev) {
                        activateModification(monster, ev.currentTarget);
                        const gotcha = document.createElement('A');
                        document.body.appendChild(gotcha);
                        document.body.appendChild(document.createElement('BR'));
                        gotcha.innerHTML = el.fileName + ': ' + newName + ', variation of ' + group;
                        monster.head.name[redefine] = start;
                        if(!monster.head.extraNotes) monster.head.extraNotes = [];
                        monster.head.extraNotes.push({
                            type: 'variation', // variantion of a monster do not confuse with 'variant'! Some sort of sub-type, while variants are the same monsters growing or getting older etc.
                            value: newName
                        });
                        gotcha.href = URL.createObjectURL(new Blob([ JSON.stringify(monster, null, 4) ], { type: "application/json" }));
                        gotcha.download = el.fileName;
                        gotcha.click();
                    };
                    el.hints.push(button);
                    el.td.appendChild(button);
                    
                }
            }
        }
    }
    
    function extractGroup(monsterNames, group) {
        for(let loop = 0; loop < monsterNames.length; loop++) {
            const comma = monsterNames[loop].indexOf(',');
            const start = monsterNames[loop].substring(0, comma).trim().toLowerCase();
            if(start === group.toLowerCase()) monsterNames[loop] = monsterNames[loop].substr(comma + 1).trim();
        }
    }
    
    function makeButton(fileName, buttonInner, monster, feedbackInner, modificationCallback) {
        const build = document.createElement('BUTTON');
        build.innerHTML = buttonInner;
        build.onclick = function() {
            activateModification(monster, build);
            const gotcha = document.createElement('A');
            document.body.appendChild(gotcha);
            document.body.appendChild(document.createElement('BR'));
            gotcha.innerHTML = fileName + ',' + feedbackInner;
            modificationCallback();
            gotcha.href = URL.createObjectURL(new Blob([ JSON.stringify(monster, null, 4) ], { type: "application/json" }));
            gotcha.download = fileName;
            gotcha.click();
        }
        return build;
    }
};
