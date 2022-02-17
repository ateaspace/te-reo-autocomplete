$(document).ready(function(){

    var lastSent = "";
    var removedWords = ""; 
    var KEY_TAB = 9;
    var maxSuggestions = 3;
    var suggCount = 0;
    var sugs = [];
    var state;
    var textareaX;
    var textareaY;
    var selection;
    var positivePhrases;
    var negativePhrases;
    var customPhrases;
    var lastTopSuggestion;
    var inputElement = document.getElementById("inputString");

    updateLists();
    onTopKChange(maxSuggestions);

    // disable default TAB action
    $("#inputString").on('keydown', function(e) {
        if (e.which == KEY_TAB) { 
            e.preventDefault();
        }
    });

    $("#inputString").on('keyup', function(e) {
        processOnChange(e, false);
    });

    function defineOnClickListeners() {
        $("#divResult0").click(function(e){ suggClicked(e, "divResult0") });
        $("#divResult1").click(function(e){ suggClicked(e, "divResult1") });
        $("#divResult2").click(function(e){ suggClicked(e, "divResult2") });
        $("#divResult3").click(function(e){ suggClicked(e, "divResult3") });
        $("#divResult4").click(function(e){ suggClicked(e, "divResult4") });
    
        $("#cross0").click(function(){ crossClicked("crs0") });
        $("#cross1").click(function(){ crossClicked("crs1") });
        $("#cross2").click(function(){ crossClicked("crs2") });
        $("#cross3").click(function(){ crossClicked("crs3") });
        $("#cross4").click(function(){ crossClicked("crs4") });
    
        $("#tick0").click(function(){ tickClicked("tck0") });
        $("#tick1").click(function(){ tickClicked("tck1") });
        $("#tick2").click(function(){ tickClicked("tck2") });
        $("#tick3").click(function(){ tickClicked("tck3") });
        $("#tick4").click(function(){ tickClicked("tck4") });
    }
    

    $("#inputString").on('mousedown', function(e) {
        $("div.selectpopup").fadeOut(200);
    });
    
    $("#popuptick").click(function(){ popupClicked("tick") });

    document.getElementById("dropdownTopK").onchange = function() {
        maxSuggestions = parseInt(document.getElementById("dropdownTopK").value);
        onTopKChange(maxSuggestions);
    };

    function processOnChange(e, suggClicked, suggestion) {
        inputElement.focus();
        if (!e) e = window.event;
        var cursorLoc = inputElement.selectionStart; // current cursor location
        var input = $("#inputString").val(); // entire input text
        var beforeCursor = input.substring(0, cursorLoc); // all text before cursor location
        var currSentence = beforeCursor.split(/[.?!]\s|[\\\n\r\t]/).pop(); // all text between cursor location and last end-of-sentence character
        removedWords = "";

        // console.log(e.which);
        if (e.which == KEY_TAB || suggClicked) { // insert selection
            // console.log("state: " + state);
            if ((sugs[0] != undefined && state == "pt") || (suggClicked)) { 
                var currSuggestion;
                if (suggClicked) {
                    currSuggestion = suggestion;
                } else {
                    currSuggestion = sugs[0];
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
        } else { // update suggestions
            if (currSentence.length < 2 || currSentence.trim().length == 0) { // prune single and empty character inputs 
                setResult("no suggestion");
            } else if (lastSent != currSentence) { // prevent duplicate requests
                suggCount = 0;
                sugs = [];
                getSuggestions(currSentence, "", 0);
            }
        }
    }

    function getSuggestions(currentSentence, suggPrefix, startPos) {
        // create get request with value of text field
        $.ajax({url: "pat", type: "get", dataType: "json", data: {inputString: currentSentence, topk: maxSuggestions}, success: function(result) { 
            state = result.state;
            for (var i = 0; i < maxSuggestions - startPos; i++) {
                var propName = "sg" + i;
                if (result[propName]) {
                    sugs.push(suggPrefix + result[propName]);
                    suggCount++;
                } else sugs.push(result[propName]);
            }
            sugs.length = suggCount;
            setResults(sugs, state);

            if (state == "ns" || suggCount < maxSuggestions) {
                if (sanitize(currentSentence) == sanitize(lastTopSuggestion)) { // if input matches 
                    sugs.length = 0;
                    sugs.push(lastTopSuggestion);
                    setResults(sugs, "pt");
                } else {
                    if (currentSentence.split(" ").length > 1) {
                        var newSentence = currentSentence.substring(currentSentence.indexOf(" ") + 1);
                        removedWords += currentSentence.split(" ")[0] + " ";
                        getSuggestions(newSentence, removedWords, suggCount);
                    } else {
                        setResult("no suggestion");
                    }
                }                
            } else if (state == "ft" || state == "pt") { // if suggestions exist
                if (positivePhrases.length > 0) {
                    var changeMade = false;
                    for (var i = 0; i < sugs.length; i++) {
                        for (var j = 0; j < positivePhrases.length; j++) {
                            if (sugs[i] == positivePhrases[j]) {
                                var tmp = sugs[0]; // save top
                                sugs[0] = sugs[i]; // set top to match
                                sugs[i] = tmp; // set match to top (swap)
                                changeMade = true;
                                setResults(sugs, "pt");
                            }
                        }
                    }
                    if (!changeMade) setResults(sugs, state);
                } 
                if (negativePhrases.length > 0) {
                    for (var i = 0; i < sugs.length; i++) {
                        for (var j = 0; j < negativePhrases.length; j++) {
                            if (sugs[i] == negativePhrases[j]) {
                                sugs.splice(i, 1); // remove from suggestion
                                i--; // array is re-indexed on splice - move back one in array
                            }
                        }
                    }
                    setResults(sugs, state);
                }                       
            } else {
                console.log("given state does not exist: " + state);
            }
            if (customPhrases.length > 0) {
                var changeMade = false;
                for (var j = 0; j < customPhrases.length; j++) {
                    if (sanitize(customPhrases[j]).startsWith(sanitize(currentSentence))) {
                        // if (levenshtein(sanitize(customPhrases[j]), sanitize(currentSentence)) < 5) {};
                        if ((sanitize(customPhrases[j]).length / 2) < sanitize(currentSentence).length) {
                            console.log("half of custPhrase length: " + sanitize(customPhrases[j]).length / 2);
                            console.log("curr length: " + sanitize(currentSentence).length);
                            sugs.unshift(customPhrases[j]);
                            sugs = sugs.slice(0, 3);
                            setResults(sugs, "pt");
                            changeMade = true;
                        }
                    }
                }
                if (!changeMade) setResults(sugs, state);
            }   
            lastSent = currentSentence;
        }}); 
    }

    function sanitize(str) {
        return str.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
    }

    function sanitizeDiacriticsOnly(str) {
        return str.normalize("NFD").replace(/\p{Diacritic}/gu, "");
    }

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

    $(document).on('mousedown', function(e) {
        textareaX = e.pageX;
        textareaY = e.pageY;
        $("div.selectpopup").fadeOut(200);
    });


    $("#listbutton").click(function(){
        if ($(".listContainer").css("max-height") == "0px") {
            $(".listContainer").css("max-height", "40vh");
            $(".listContainer").css("border", "1px solid rgb(170, 31, 37)");
            $("#cog").css("transform", "rotate(-90deg)");
            // $("#dropdownTopK").css("visibility", "visible");
            // button.textContent = "Hide Lists";
        } else {
            $(".listContainer").css("max-height", "0vh");
            $(".listContainer").css("border", "none");
            $("#cog").css("transform", "rotate(0deg)");
            // $("#dropdownTopK").css("visibility", "hidden");
            // button.textContent = "Show Lists";
        }
    });

    $(document).on('click', '.listPhrase', function(e) {
        listItemClicked(e.target.id);
    });

    function updateLists() {
        console.log("updating lists...")
        positivePhrases = JSON.parse(localStorage.getItem("storePos"));
        negativePhrases = JSON.parse(localStorage.getItem("storeNeg"));
        customPhrases = JSON.parse(localStorage.getItem("storeCus"));
        var positiveListDiv = document.getElementById("positiveList").getElementsByTagName("p")[0];
        var negativeListDiv = document.getElementById("negativeList").getElementsByTagName("p")[0];
        var customListDiv = document.getElementById("customList").getElementsByTagName("p")[0];
        positiveListDiv.innerHTML = "";
        negativeListDiv.innerHTML = "";
        customListDiv.innerHTML = "";

        for (var idx = 0; idx < positivePhrases.length; idx++) {
            positiveListDiv.innerHTML += "<div class=\"listPhrase\" id=\"plist" + idx + "\" title=\"Click to remove suggestion from list\">" + positivePhrases[idx] + "</div>";
        }
        for (var idx = 0; idx < negativePhrases.length; idx++) {
            negativeListDiv.innerHTML += "<div class=\"listPhrase\" id=\"nlist" + idx + "\" title=\"Click to remove suggestion from list\">" + negativePhrases[idx] + "</div>";
        }
        for (var idx = 0; idx < customPhrases.length; idx++) {
            customListDiv.innerHTML += "<div class=\"listPhrase\" id=\"clist" + idx + "\" title=\"Click to remove suggestion from list\">" + customPhrases[idx] + "</div>";
        }
    }

    function listItemClicked(id) {
        if (id.startsWith("plist")) {
            removeFromList(positivePhrases[id.slice(-1)], "storePos");
        } else if (id.startsWith("nlist")) {
            removeFromList(negativePhrases[id.slice(-1)], "storeNeg");
        } else if (id.startsWith("clist")) {
            removeFromList(customPhrases[id.slice(-1)], "storeCus");
        } else {
            console.log("ERROR: list item id not recognized: " + id);
        }
    }

    function removeFromList(phrase, listType) {
        var currlist = JSON.parse(localStorage.getItem(listType));
        if (currlist == null) {
            console.log("List to remove from is null");
        } else if (currlist.includes(phrase)) {
            currlist.splice(currlist.indexOf(phrase), 1); // remove phrase
            localStorage.setItem(listType, JSON.stringify(currlist));
        } else {
            console.log("Phrase doesn't exist in list");
        }
        updateLists();
    }

    function popupClicked(type) {
        if (type == "tick") {
            if (stringValid(selection)) {
                if (customPhrases == null) {
                    addEntry("storeCus", selection);
                } else if (!customPhrases.includes(selection)) {
                    addEntry("storeCus", selection);
                } else {
                    console.log("Phrase already exists in custom list: " + selection);
                }
            }
        } else {
            console.error("popupClicked() doesn't accept type: " + type);
        }
    }

    function suggClicked(e, div) {
        var clickedDivText = document.getElementById(div).textContent;
        if (stringValid(clickedDivText)) processOnChange(e, true, clickedDivText);
        else console.log("Invalid string: " + clickedDivText);
    }

    function addEntry(storageName, entryValue) { 
        if (localStorage.getItem(storageName) == "") {
            localStorage.setItem(storageName, JSON.stringify([entryValue]));
        } else {
            var existingEntries = JSON.parse(localStorage.getItem(storageName)) || [];
            // Save allEntries back to local storage
            existingEntries.push(entryValue);
            localStorage.setItem(storageName, JSON.stringify(existingEntries));
        }
        updateLists();
    };

    function stringValid(str) {
        if (str == null || str == "" || str == undefined || str == "no suggestion") {
            return false;
        } else {
            return true;
        }
    }

    function tickClicked(tick) {
        var positiveSuggestion;
        var positiveSuggestionIndex;
        for (var i = 0; i < maxSuggestions; i++) {
            if (tick == "tck" + i && stringValid(sugs[i])) {
                positiveSuggestion = sugs[i];
                positiveSuggestionIndex = i;
                break;
            }
        }
        if (positivePhrases == null) {
            addEntry("storePos", positiveSuggestion);
        } else if (!positivePhrases.includes(positiveSuggestion)) {
            addEntry("storePos", positiveSuggestion);
        } else {
            console.log("Phrase already exists in positive list: " + positiveSuggestion);
        }
    }

    function crossClicked(cross) {
        console.log("cross clicked: " + cross);
        var negativeSuggestion;
        var negativeSuggestionIndex;
        for (var i = 0; i < maxSuggestions; i++) {
            if (cross == "crs" + i && stringValid(sugs[i])) {
                negativeSuggestion = sugs[i];
                negativeSuggestionIndex = i;
                break;
            }
        }
        if (negativePhrases == null) {
            addEntry("storeNeg", negativeSuggestion);
        } else if (!negativePhrases.includes(negativeSuggestion)) {
            addEntry("storeNeg", negativeSuggestion);
        } else {
            console.log("Phrase already exists in negative list: " + negativeSuggestion);
        }
        sugs.splice(negativeSuggestionIndex, 1);
        setResults(sugs, state);
    }

    function onTopKChange(topk) {
        document.getElementById("suggestions").innerHTML = "";
        for (var i = 0; i < topk; i++) {
            var newHorizontalFlexDiv = document.createElement("div");
            newHorizontalFlexDiv.className = "flex-horizontal";

            var newTickImage = document.createElement("img");
            newTickImage.src = "images/tick.png"; 
            newTickImage.className = "tick";
            newTickImage.id = "tick" + i;
            newTickImage.title = "Click to see this suggestion more";

            var newCrossImage = document.createElement("img");
            newCrossImage.src = "images/cross.png"; 
            newCrossImage.className = "cross";
            newCrossImage.id = "cross" + i;
            newCrossImage.title = "Click to not see this suggestion";

            var newSuggestionDiv = document.createElement("div");
            newSuggestionDiv.className = "result";
            newSuggestionDiv.id = "divResult" + i;
            newSuggestionDiv.title = "Click to add this suggestion to text box";

            document.getElementById("suggestions").appendChild(newHorizontalFlexDiv);
            newHorizontalFlexDiv.appendChild(newTickImage);
            newHorizontalFlexDiv.appendChild(newCrossImage);
            newHorizontalFlexDiv.appendChild(newSuggestionDiv);
        }
        defineOnClickListeners();
    }

    // writes given string to result div
    function setResult(s) {
        $("#divResult0").css({"color" : "grey"}); 
        $("#divResult0").text(s); 
        $("#divResult0").prop("title", ""); 
        $("#divResult0").css({"margin-left" : "-10%"}); 
        for (var i = 1; i < maxSuggestions; i++) {
            $("#divResult" + i).text(""); 
        }
        renderCrossImages();
    }

    // writes given string to result div
    function setResults(sgs, st) {
        resetResults();
        state = st;
        $("#divResult0").css({"margin-left" : "0"}); 
        if (sgs[0]) lastTopSuggestion = sgs[0];
        if (st == "pt") $("#divResult0").css({'color':'black'}); 
        else $("#divResult0").css({'color':'grey'}); 
        for (var i = 0; i < maxSuggestions; i++) $("#divResult" + i).text(sgs[i]);
        renderCrossImages();
    }

    function showImage(id) {
        document.getElementById(id).style.visibility = "visible";
    }

    function hideImage(id) {
        document.getElementById(id).style.visibility = "hidden";
    }

    function resetResults() {
        for (var i = 0; i < maxSuggestions; i++) $("#divResult" + i).text(""); 
    }

    function renderCrossImages() {
        if ($("#divResult0").text() == "" || $("#divResult0").text() == "no suggestion") {
            hideImage("cross0");
            hideImage("tick0");
        } 
        else {
            showImage("cross0");
            showImage("tick0");
        }
        for (var i = 1; i < maxSuggestions; i++) {
            if ($("#divResult" + i).text() == "") {
                hideImage("cross" + i);
                hideImage("tick" + i);
            }
            else {
                showImage("cross" + i);
                showImage("tick" + i);
            }
        }
    }
});