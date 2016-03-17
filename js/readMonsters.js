
window.onload = function() {
	var parseListFeedback = document.getElementById('parseListFeedback');
	var realDeal = document.getElementById('realDeal');
	var realDealFeedback = document.getElementById('realDealFeedback');
	parseListFeedback.parentNode.removeChild(parseListFeedback);
	realDeal.parentNode.removeChild(realDeal);
	realDealFeedback.parentNode.removeChild(realDealFeedback);
	document.getElementById('listInput').onchange = function(ev) {
		alert("something something!");
	};
}
