"use strict";

const dragonAgeCategory = [
    'wyrmling',        'very young',  'young',
    'juvenile',        'young adult', 'adult',
    'mature adult',    'old',         'very old',
    'ancient',         'wyrm',        'great wyrm'
];

window.onload = function() {
    const start = document.getElementById('loader');
    const nameTable = document.getElementById('nameConditioning');
    const errorSpan = countElements('th', nameTable.getElementsByTagName('TR')[0]) - 1;
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
        if(loadIndex === files.length) return;
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
            tr.innerHTML += cell(obj.head.name);
            const td = document.createElement('TD');
            let hints = suggestFileAndHeaderHints(files[loadIndex], obj, tr, td);
            if(!hints) return;
            tr.appendChild(td);
            for(let loop = 0; loop < hints.length; loop++) td.appendChild(hints[loop]);
            nameTable.appendChild(tr);
        };
        reader.readAsText(files[loadIndex]);
    }
    
    function suggestFileAndHeaderHints(file, monster, tr, td) {
        const invalidChars = /[^ a-zA-Z0-9'\u2019]/g;
        let note = [];
        for(let loop = 0; loop < monster.head.name.length; loop++) {
            const name = monster.head.name[loop];
            if(name !== name.trim()) {
                const apply = document.createElement('BUTTON');
                apply.innerHTML = 'trim name[' + loop + '].';
                apply.onclick = function() {
                    apply.disabled = true;
                    const gotcha = document.createElement('A');
                    document.body.appendChild(gotcha);
                    document.body.appendChild(document.createElement('BR'));
                    gotcha.innerHTML = file.name + ', trimmed name[' + loop + ']';
                    monster.head.name[loop] = monster.head.name[loop].trim();
                    gotcha.href = URL.createObjectURL(new Blob([ JSON.stringify(monster, null, 4) ], { type: "application/json" }));
                    gotcha.download = file.name;
                    gotcha.click();
                }
                note.push(apply);
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
                    const apply = document.createElement('BUTTON');
                    apply.innerHTML = 'AGE: "' + age + '" to annotation';
                    apply.onclick = function() {
                        apply.disabled = true;
                        const gotcha = document.createElement('A');
                        document.body.appendChild(gotcha);
                        document.body.appendChild(document.createElement('BR'));
                        gotcha.innerHTML = file.name + ', ' + monster.head.name[loop] + ': changed to "' + newName + '" and added annotation [[' + age + ']]';
                        monster.head.name[loop] = newName;
                        if(!monster.head.extraNotes) monster.head.extraNotes = [];
                        monster.head.extraNotes.push({
                            type: 'ageCategory',
                            value: age
                        });
                        gotcha.href = URL.createObjectURL(new Blob([ JSON.stringify(monster, null, 4) ], { type: "application/json" }));
                        gotcha.download = file.name;
                        gotcha.click();
                    }
                    note.push(apply);
                    continue;
                }
                note.push(span('name[' + loop + '] contains odd chars.'));
                note.push(document.createElement('BR'));
            }
        }
        if(note.length === 0) note = null;
        return note;
    }
};
