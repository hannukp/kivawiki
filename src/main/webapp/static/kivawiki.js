function hexdump(txt) {
	var hex = '', tmp;
	if (txt) {
		for (var i = 0, j = txt.length; i < j; i++) {
			tmp = txt.charCodeAt(i).toString(16);
			hex += ( (tmp.length == 2) ? tmp : '0' + tmp );
			hex += ' ';
		}
	}
	return hex;
}


function getCaretPosition(el) {
	el.focus();
	if (document.selection) {
		var sel = document.selection.createRange();
		sel.moveStart ('character', -el.value.length);
		return sel.text.length;
	} else if (el.selectionStart || el.selectionStart == '0') {
		return el.selectionStart;
	}
	return 0;
}

function setCaretPosition(el, pos) {
	el.focus();
	if (el.setSelectionRange) {
		el.setSelectionRange(pos,pos);
	} else if (el.createTextRange) {
		var matches = el.value.substring(0, pos).match(/\n/gm);
		var line = matches ? matches.length : 0;
		pos -= line;
		var range = el.createTextRange();
		range.collapse(true);
		range.moveEnd('character', pos);
		range.moveStart('character', pos);
		range.select();
	}
}

function setCaretPositionByLine(el, line) {
	var text = el.value;
	var pos = 0;
	var currentLine = 1;
	while (currentLine < line) {
		pos = text.indexOf('\n', pos) + 1;
		currentLine++;
		if (pos == 0) {
			pos = text.length;
			break;
		}
	}
	setCaretPosition(el, pos);
}

function repeatChar(c, times) {
	var array = [];
	for (var i = 0; i < times; i++) {
		array[array.length] = c;
	}
	return array.join('');
}

function autocomplete(f, e) {
	var pos = getCaretPosition(f); //f.getSelection().start
	//setCaretPosition(f, pos);
	//return;
	
	if (pos == 0) return;
	var data = f.value;
	var rowStart = data.lastIndexOf("\n", pos-1);
	var lineRe = /^(\|)?(=+|-+|\++|\%+|\*+|\^+|~+)$/
	var expansionLength = 79;
	if (rowStart > 0) {
		prevRowStart = data.lastIndexOf("\n", rowStart-1);
		var len = $.trim(data.substring(prevRowStart+1, rowStart)).length;
		if (len > 0)
			expansionLength = len;
	}
	var mo = lineRe.exec(data.substring(rowStart+1, pos))
	if (mo) {
		var subst = (mo[1] || '') + repeatChar(mo[2].substr(0,1), expansionLength);
		data = data.substring(0, rowStart+1) + subst + data.substring(pos);
		f.value = data;
		setCaretPosition(f, rowStart+1 + subst.length);
	}
}

function editorCode() {
	$(document).ready(function() {
	 	$(".autocomplete").keypress(function(e) {
			if (e.ctrlKey && (e.keyCode === 32 || e.charCode === 32)) {
				autocomplete(e.target);
				e.preventDefault();
				e.stopPropagation();
				return true;
			}
		});
	 	
	 	function formatTimePart(t) {
	 		t = "" + t;
	 		if (t.length == 1) {
	 			t = "0" + t;
	 		}
	 		return t;
	 	}
	 	
	 	function formatTime(d) {
	 		return d.getHours() + ":" + formatTimePart(d.getMinutes()) + ":" + formatTimePart(d.getSeconds());
	 	}
	 	
	 	var previousText = $(".edit textarea").val();
		var saveDraftUrl = $('#save_draft').attr("href");
	 	
	 	setInterval(function() {
	 		var text = $(".edit textarea").val();
	 		if (text != previousText) {
	 			previousText = text;
	 			
	 			$.ajax(saveDraftUrl, {
	 				"type": "post"
	 				,"data": { "data":text }
	 				,"error": function(jqXHR, textStatus, errorThrown) {
	 		 			$('#draft_saved').text("( ERROR SAVING DRAFT! COPY YOUR WORK TO A FILE! )");
	 		 			$('.edit input[type="submit"]').attr("disabled", "disabled");
	 				}
	 				,"success": function(data, textStatus, jqXHR) {
	 		 			var d = new Date();
	 		 			$('#draft_saved').text("(saved at " + formatTime(d) + ")");
	 		 			$('.edit input[type="submit"]').removeAttr("disabled");
	 				}
	 			});
	 		}
	 	}, 60 * 1000);
	});
}

$(document).ready(function() {
	var highlightColor = '#eea';
	var backgroundColor = '#fff';
	
	function getHeader(el) {
		return $("a[name=" + $(el).attr("href").substr(1) + "]").parent();
	}

	function fadeout(h, speed) {
		h.animate(
			{ backgroundColor: backgroundColor }, 
			speed, 
			'linear',
			function() {
				h.css("background-color", h.data('hovered') ? highlightColor : "transparent");
			}
		);
	}

	$(".article .toc a").hover(
		function(e) {
			var h = getHeader(this);
			h.data('hovered', true);
			h.data('clicked', false);
			h.css("background-color", highlightColor);
		},
		function(e) {
			var h = getHeader(this);
			h.data('hovered', false);
			if (!h.data('clicked')) {
				fadeout(h, 200);
			}
		}
	).click(
		function(e) {
			var h = getHeader(this);
			h.data('clicked', true);
			setTimeout(function() { fadeout(h, 1000); }, 1500);
		}
	);
});
