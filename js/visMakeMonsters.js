"use strict";

window.onload = function() {
    {
        const start = document.getElementById('loader');
        start.onchange = function(el) {
            start.disabled = true;
            recursiveLoad(start.files);
        };
    }

    const structVis = document.getElementById('structureVisualization');
    const monsters = [];
    function recursiveLoad(files, loadIndex) {
        if(loadIndex === undefined) loadIndex = 0;
        if(loadIndex === files.length) {
            makeProtobufferblob();
            return;
        }
        const reader = new FileReader();
        reader.onload = function() {
            const mob = JSON.parse(reader.result);
            const sameName = matchMonsterByNames(mob.head.name);
            if(sameName) {
                if(!sameName.variations) sameName.variations = [];
                sameName.variations.push(mob);
                mob.head.name = null;
            }
            else {
                monsters.push({
                    first: mob,
                    variations: null
                });
            }
            recursiveLoad(files, loadIndex + 1);
        };
        reader.readAsText(files[loadIndex]);
    }
    
    function makeProtobufferblob() {
        for(let loop = 0; loop < monsters.length; loop++) {
            const mob = monsters[loop];
            if(!mob.variations) {
                const tr = document.createElement('TR');
                tr.appendChild(cell(loop));
                const nameCell = cell(stringFromList(mob.first.head.name, '<br/>'));
                if(!mob.first.head.extraNotes) nameCell.colSpan = 2;
                tr.appendChild(nameCell);
                if(mob.first.head.extraNotes) tr.appendChild(cell(stringFromVariations(mob.first.head)));
                structVis.appendChild(tr);
                continue;
            }
            for(let inner = 0; inner < mob.variations.length + 1; inner++) {
                const tr = document.createElement('TR');
                let variation = '';
                if(inner === 0) {
                    let td = cell(loop + '*' + (mob.variations.length + 1));
                    td.rowSpan = mob.variations.length + 1;
                    tr.appendChild(td);
                    td = cell(stringFromList(mob.first.head.name));
                    td.rowSpan = mob.variations.length + 1;
                    tr.appendChild(td);
                    variation = '<em>main</em><br/>' + stringFromVariations(mob.first.head);
                }
                else variation = stringFromVariations(mob.variations[inner - 1].head);
                tr.appendChild(cell(variation));
                structVis.appendChild(tr);
            }
        }
            
            
        alert('loaded ' + monsters.length + ' entries');
        
    }
    
    function matchMonsterByNames(nameList) {
        for(let loop = 0; loop < monsters.length; loop++) {
            const mob = monsters[loop];
            if(mob.first.head.name.length !== nameList.length) continue;
            let check;
            for(check = 0; check < nameList.length; check++) {
                if(nameList[check] !== mob.first.head.name[check]) break;
            }
            if(check === nameList.length) return mob;
        }
        return null;
    }
    
    function stringFromVariations(header) {
        if(!header.extraNotes) return '';
        let res = '';
        for(let loop = 0; loop < header.extraNotes.length; loop++) {
            const note = header.extraNotes[loop];
            if(loop) res += '<br/>';
            switch(note.type) {
            case 'publisher': res += 'BY: ' + (note.value || '3pp'); break;
            case 'ageCategory': res += note.value; break;
            case 'elemCategory': res += note.value; break;
            case 'group': res += note.value; break;
            case 'appliedTemplate': res += '+' + note.value; break;
            case 'variant.morphTarget': res += 'MORPH: ' + note.value; break;
            case 'variant.size': res += 'SIZE: ' + note.value; break;
            case 'extraInfo': res += 'MISC: ' + note.value; break;
            case 'variation': res += '&gt;' + note.value; break;
            
            default: alert('Unknown extra: ' + note.type);
            }
        }
        return res;
    }
}


function cell(innerHTML) {
    const res = document.createElement('TD');
    res.innerHTML = innerHTML;
    return res;
}


function stringFromList(array, separator) {
    let res = '';
    for(let loop = 0; loop < array.length; loop++) {
        if(res.length) res += separator;
        res += array[loop];
    }
    return res;
}

