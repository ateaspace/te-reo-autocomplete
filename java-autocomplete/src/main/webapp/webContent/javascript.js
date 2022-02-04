var lastSent = "";
var KEY_TAB = 9;
var sug0;
var sug1;
var sug2;
var state;
var textareaX;
var textareaY;
var selection;

// disable default TAB action
$("#inputString").on('keydown', function(e) {
    if (e.which == KEY_TAB) { 
        e.preventDefault();
    }
});

function processOnChange(e, suggClicked, suggestion) {
    var inputElement = document.getElementById("inputString");
    inputElement.focus();
    if (!e) e = window.event;
    var cursorLoc = inputElement.selectionStart; // current cursor location
    var input = $("#inputString").val(); // entire input text
    var beforeCursor = input.substring(0, cursorLoc); // all text before cursor location
    var currSentence = beforeCursor.split(/[.?!]\s|[\\\n\r\t]/).pop(); // all text between cursor location and last end-of-sentence character

    // console.log(e.which);
    if (e.which == KEY_TAB || suggClicked) { 
        // console.log("state: " + state);
        if ((sug0 != undefined && state == "pt") || (suggClicked)) {
            var currSuggestion;
            if (suggClicked) {
                currSuggestion = suggestion;
            } else {
                currSuggestion = sug0;
            }
            var newCursorLoc = cursorLoc + (currSuggestion.length - currSentence.length); // calculate new cursor position

            inputElement.setSelectionRange(cursorLoc-currSentence.length, cursorLoc); // select currSentence
            var beforeCurrSentence = input.slice(0, cursorLoc-currSentence.length); // get all text before currSentence
            var afterCurrSentence = input.slice(cursorLoc); // get all text after currSentence
            var newOut = beforeCurrSentence + currSuggestion + afterCurrSentence; // replace currSentence with suggestion

            $("#inputString").val(newOut); // push suggestion to text box
            inputElement.setSelectionRange(newCursorLoc, newCursorLoc); // set new cursor position
            state = "ns";
            setResult("no suggestion");
        }
    } else {
        if (currSentence.length < 2 || currSentence.trim().length == 0) { // prune single and empty character inputs 
            setResult("no suggestion");
        } else if (lastSent != currSentence) { // prevent duplicate requests
            // create get request with value of text field
            $.ajax({url: "pat", type: "get", dataType: "json", data: {inputString: currSentence}, success: function(result) { 
                state = result.state;
                if (state == "ns") { // no suggestion
                    setResult("no suggestion");
                } else if (state == "ft") { // failed threshold
                    sug0 = result.sg0; 
                    sug1 = result.sg1; 
                    sug2 = result.sg2;
                    setResults(sug0, sug1, sug2, state);
                    sug1 = undefined;
                } else if (state == "pt") { // passed threshold
                    sug0 = result.sg0; 
                    sug1 = result.sg1; 
                    sug2 = result.sg2;
                    setResults(sug0, sug1, sug2, state);
                } else {
                    console.log("given state does not exist: " + state);
                }
                lastSent = currSentence;
            }}); 
        }
    }
}

$("#inputString").on('keyup', function(e) {
    processOnChange(e, false);
});

$("#divResult0").click(function(e){ suggClicked(e, "divResult0") });
$("#divResult1").click(function(e){ suggClicked(e, "divResult1") });
$("#divResult2").click(function(e){ suggClicked(e, "divResult2") });

$("#cross0").click(function(){ crossClicked("crs0") });
$("#cross1").click(function(){ crossClicked("crs1") });
$("#cross2").click(function(){ crossClicked("crs2") });

$("#inputString").on('mouseup', function(e) {
    var inputElement = document.getElementById("inputString");
    var selectionStart = inputElement.selectionStart;
    var selectionEnd = inputElement.selectionEnd;
    selection = inputElement.value.substring(selectionStart, selectionEnd);
    if (selection.length > 0 && selection.trim() != "") {
        console.log("selection: " + selection);
        $("div.selectpopup").css({
            'left': textareaX -20,
            'top': textareaY -78
        }).fadeIn(200);
    } else {
        $("div.selectpopup").fadeOut(200);
    }
});

$("#inputString").on('mousedown', function(e) {
    $("div.selectpopup").fadeOut(200);
});

$(document).ready(function(e) {
    $(document).on('mousedown', function(e) {
        textareaX = e.pageX;
        textareaY = e.pageY;
        $("div.selectpopup").fadeOut(200);
    });
});

$("#popuptick").click(function(){ popupClicked("tick") });
$("#popupcross").click(function(){ popupClicked("cross") });

function popupClicked(type) {
    if (type == "tick") {
        if (stringValid(selection)) {
            $.ajax({url: "pat", type: "post", dataType: "json", data: { custom: selection }, success: function(result) {
                console.log("POST request sent with: " + selection);
                console.log("result: " + result);
            }});
        }
    } else if (type == "cross") {
        console.log("cross clicked");
    } else {
        console.error("popupClicked() doesn't accept type: " + type);
    }
}

function suggClicked(e, div) {
    var positiveSuggestion;
    var clickedDivText = document.getElementById(div).textContent;

    if (stringValid(clickedDivText)) {
        positiveSuggestion = clickedDivText;
        processOnChange(e, true, positiveSuggestion);
        
        $.ajax({url: "pat", type: "post", dataType: "json", data: { positive: positiveSuggestion }, success: function(result) {
            console.log("POST request sent with: " + positiveSuggestion);
            console.log("result: " + result);
        }});
    }
}

function stringValid(str) {
    if (str == null || str == "" || str == undefined || str == "no suggestion") {
        return false;
    } else {
        return true;
    }
}

function crossClicked(cross) {
    var negativeSuggestion;
    if (cross == "crs0" && stringValid(sug0)) {
        negativeSuggestion = sug0;
    } else if (cross == "crs1" && stringValid(sug1)) {
        negativeSuggestion = sug1;
    } else if (cross == "crs2" && stringValid(sug2)) {
        negativeSuggestion = sug2;
    } else {
        console.log("given cross does not exist: " + cross);
    }
    $.ajax({url: "pat", type: "post", dataType: "json", data: { negative: negativeSuggestion }, success: function(result) {
        console.log("POST request sent with: " + negativeSuggestion);
        console.log("result: " + result);        
    }});
}

// writes given string to result div
function setResult(s) {
    $("#divResult0").css({'color':'grey'}); 
    $("#divResult0").text(s); 
    $("#divResult1").text(""); 
    $("#divResult2").text(""); 
    renderCrossImages();
}

// writes given string to result div
function setResults(s0, s1, s2, st) {
    resetResults();
    if (st == "pt") {
        $("#divResult0").css({'color':'black'}); 
    } else {
        $("#divResult0").css({'color':'grey'}); 
    }
    $("#divResult0").text(s0); 
    $("#divResult1").text(s1); 
    $("#divResult2").text(s2); 
    renderCrossImages();
}

function showImage(id) {
    document.getElementById(id).style.visibility = "visible";
}

function hideImage(id) {
    document.getElementById(id).style.visibility = "hidden";
}

function resetResults() {
    $("#divResult0").text(""); 
    $("#divResult1").text(""); 
    $("#divResult2").text(""); 
}

function renderCrossImages() {
    if ($("#divResult0").text() == "" || $("#divResult0").text() == "no suggestion") hideImage("cross0");
    else showImage("cross0");
    if ($("#divResult1").text() == "") hideImage("cross1");
    else showImage("cross1");
    if ($("#divResult2").text() == "") hideImage("cross2");
    else showImage("cross2");
}