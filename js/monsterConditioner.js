"use strict";

window.onload = function() {
    const start = document.getElementById('loader');
    const nameTable = document.getElementById('nameConditioning');
    const errorSpan = countElements('th', nameTable.getElementsByTagName('TR')[0]) - 1;
    start.onchange = function(el) {
        start.disabled = true;
        recursiveLoad(start.files);
    };
    
    function loadData(file) {
        return {}; // magic, that's async.
    }
    
    function cell(innerHTML, colSpan) {
        let res = '<td' + (colSpan? ' colspan="' + colSpan + '"' : '') + '>';
        res += innerHTML;
        return res + '</td>';
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
            let hints = suggestFileAndHeaderHints(files[loadIndex].name, obj.head, tr, td);
            if(!hints) return;
            tr.appendChild(td);
            for(let loop = 0; loop < hints.length; loop++) td.appendChild(hints[loop]);
            nameTable.appendChild(tr);
        };
        reader.readAsText(files[loadIndex]);
    }
    
    function suggestFileAndHeaderHints(filename, header, tr, td) {
        let dummy = document.createElement('BUTTON');
        dummy.innerHTML = 'just a test';
        let another = document.createElement('BUTTON');
        another.innerHTML = 'nothing really';
        return [dummy, document.createElement('BR'), another];
    }
};
