"use strict";

window.onload = function() {
	const start = document.getElementById('loader');
	const nameTable = document.getElementById('nameConditioning');
	const errorSpan = countElements('th', nameTable.getElementsByTagName('TR')[0]) - 1;
	start.onchange = function(el) {
		start.disabled = true;
		for(let load = 0; load < start.files.length; load++) {
			const res = loadData(start.files[load]);
			res.error = 'just testing';
			if(res.error) {
				const tr = document.createElement('TR');
				tr.innerHTML += cell(load) + cell(start.files[load].name) + cell(res.error, errorSpan);
				nameTable.appendChild(tr);
				continue;
			}
		}
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
};
