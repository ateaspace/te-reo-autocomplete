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
    inputElement.focus();

    updateLists();
    onTopKChange(maxSuggestions);

    $("#inputString").on('keydown', function(e) {
        if (e.which == KEY_TAB) { 
            e.preventDefault(); // disable default TAB action
        }
    });

    $("#inputString").on('keyup', function(e) {
        processOnChange(e, false); // get suggestions
    });

    function defineOnClickListeners() { // set result, tick and cross click listeners
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
    
    $("#inputString").on('mousedown', function(e) { $("div.selectpopup").fadeOut(200); }); // remove popup on mousedown
    
    $("#popuptick").click(function(){ popupClicked("tick") }); // text selection popup tick click listener

    document.getElementById("dropdownTopK").onchange = function() { // topk/number of suggestions dropdown listener
        maxSuggestions = parseInt(document.getElementById("dropdownTopK").value);
        onTopKChange(maxSuggestions);
    };

    function processOnChange(e, suggClicked, suggestion) { // actuated on keypress or suggestion click
        inputElement.focus();
        if (!e) e = window.event;
        var cursorLoc = inputElement.selectionStart; // current cursor location
        var input = $("#inputString").val(); // entire input text
        var beforeCursor = input.substring(0, cursorLoc); // all text before cursor location
        var currSentence = beforeCursor.split(/[.?!]\s|[\\\n\r\t]/).pop(); // all text between cursor location and last end-of-sentence character
        removedWords = ""; // words removed for subsequent queries if not enough suggestions available
        var spaceAdded = false; // whether a space has been added due to not end of sentence
        if (e.which == KEY_TAB || suggClicked) { // insert selection
            if ((sugs[0] != undefined && state == "pt") || (suggClicked)) { 
                var currSuggestion; // current suggestion
                if (suggClicked) {
                    currSuggestion = suggestion;
                } else {
                    currSuggestion = sugs[0];
                }
                if (!currSuggestion.endsWith(".") && !currSuggestion.endsWith("!") && !currSuggestion.endsWith("?")) {
                    spaceAdded = true; // add space to sentence if not end of sentence
                }

                var newCursorLoc = cursorLoc + (currSuggestion.length - currSentence.length); // calculate new cursor position
                var beforeCurrSentence = input.slice(0, cursorLoc-currSentence.length); // get all text before currSentence
                var afterCurrSentence = input.slice(cursorLoc); // get all text after currSentence
                var newOut = beforeCurrSentence + currSuggestion + afterCurrSentence; // replace currSentence with suggestion

                $("#inputString").val(newOut); // push suggestion to text box
                inputElement.setSelectionRange(newCursorLoc, newCursorLoc); // set new cursor position
            }
        }  
        // re-calculate current sentence in case space is added
        cursorLoc = inputElement.selectionStart;
        input = $("#inputString").val();
        beforeCursor = input.substring(0, cursorLoc);
        currSentence = beforeCursor.split(/[.?!]\s|[\\\n\r\t]/).pop();

        if (spaceAdded) currSentence = currSentence + " "; // if not end of sentence, append space to trigger new suggestions

        if (currSentence.length < 2 || currSentence.trim().length == 0) { // prune single and empty character inputs 
            setResult("no suggestion");
        } else if (lastSent != currSentence) { // prevent duplicate requests
            suggCount = 0;
            sugs = [];
            getSuggestions(currSentence, "", 0); // retrieve suggestions for the current sentence
        }
    }

    function getSuggestions(currentSentence, suggPrefix, startPos) {
        // create get request with currentSentence as the input string
        $.ajax({url: "pat", type: "get", dataType: "json", data: {inputString: currentSentence, topk: maxSuggestions}, success: function(result) { 
            state = result.state; // state of response (ns = no suggestion, ft = failed threshold, pt = passed threshold)
            for (var i = 0; i < maxSuggestions - startPos; i++) { // for each suggestion space required to be filled
                var propName = "sg" + i; // name of current suggestion in response
                if (result[propName]) {
                    sugs.push(suggPrefix + result[propName]); // add suggestion to suggestion list
                    suggCount++; // track number of added suggestions to ensure maximum is reached if possible
                } 
            }
            sugs.length = suggCount; // trim suggestion list to number of inserted items
            setResults(sugs, state); // update results with current suggestion (may be redundant)

            if (state == "ns" || suggCount < maxSuggestions) { // if no suggestions exist or max suggestions hasn't been reached
                if (currentSentence) {
                    if (sanitize(currentSentence) == sanitize(lastTopSuggestion)) { // if input matches the last top suggestion
                        sugs.length = 0;
                        sugs.push(lastTopSuggestion);
                        setResults(sugs, "pt"); // push last top suggestion - allows user to benefit from casing/macron/punctuation placement
                    } else {
                        if (currentSentence.split(" ").length > 1) { // if current input is more than one word
                            var newSentence = currentSentence.substring(currentSentence.indexOf(" ") + 1);
                            removedWords += currentSentence.split(" ")[0] + " ";
                            getSuggestions(newSentence, removedWords, suggCount); // get suggestions with input minus the first word
                        } else {
                            setResult("no suggestion");
                        }
                    }                
                } else {
                    setResult("no suggestion");
                }
            } else if (state == "ft" || state == "pt") { // if suggestions exist
                console.log("positivePhrases.length: " + positivePhrases.length);
                if (positivePhrases.length > 0) { // handle phrases in positive list
                    var changeMade = false;
                    for (var i = 0; i < sugs.length; i++) {
                        for (var j = 0; j < positivePhrases.length; j++) {
                            if (sugs[i] == positivePhrases[j]) {
                                sugs.unshift(sugs.splice(i, 1)[0]); // for each match in positive phrase list, move suggestion to top
                                changeMade = true;
                                setResults(sugs, "pt"); // set results with pass threshold (positive phrases shortcut the ranking threshold)
                            }
                        }
                    }
                    if (!changeMade) setResults(sugs, state); // if no matches were found in positive list, set results normally
                } 
                if (negativePhrases.length > 0) { // handle phrases in negative list
                    for (var i = 0; i < sugs.length; i++) {
                        for (var j = 0; j < negativePhrases.length; j++) {
                            if (sugs[i] == negativePhrases[j]) {
                                sugs.splice(i, 1); // for each match in negative phrase list, remove phrase from suggestions
                                i--; // array is re-indexed on splice - move back one in array
                            }
                        }
                    }
                    setResults(sugs, state); // negative phrase removal doesn't affect state
                }                       
            } else {
                console.log("given state does not exist: " + state);
            }
            if (customPhrases.length > 0) { // handle phrases in custom list
                for (var j = 0; j < customPhrases.length; j++) {
                    if (sanitize(customPhrases[j]).startsWith(sanitize(currentSentence))) {
                        if ((sanitize(customPhrases[j]).length / 2) < sanitize(currentSentence).length) { // if input matches more than half of custom phrase
                            sugs.unshift(customPhrases[j]); // move custom phrase to front
                            sugs = sugs.slice(0, maxSuggestions); // trim suggestions to match maximum suggestions
                            setResults(sugs, "pt"); // set results with pass threshold
                        }
                    }
                }
            }   
            lastSent = currentSentence; // update last sentence variable, used to prevent duplicate requests
        }}); 
    }

    function sanitize(str) { // remove diacritics and casing
        return str.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
    }

    $("#inputString").on('mouseup', function(e) { // handle text selection popup
        var inputElement = document.getElementById("inputString");            
        // get selection bounds and text
        var selectionStart = inputElement.selectionStart;
        var selectionEnd = inputElement.selectionEnd;
        selection = inputElement.value.substring(selectionStart, selectionEnd);

        if (selection.length > 3 && selection.trim() != "") { // if selection is bigger than three characters
            $("div.selectpopup").css({ // fade in popup at x y defined by onclick
                'left': textareaX -20,
                'top': textareaY -78
            }).fadeIn(200);
        } else {
            $("div.selectpopup").fadeOut(200); // fade out if selection is too small
        }
    });

    $(document).on('mousedown', function(e) { // store x y position of click for popup
        textareaX = e.pageX;
        textareaY = e.pageY;
        $("div.selectpopup").fadeOut(200);
    });

    $("#listbutton").click(function(){ // on preference click, toggle preference panel 
        if ($(".preferencePanel").css("max-height") == "0px") {
            $(".preferencePanel").css("max-height", "40vh");
            $(".preferencePanel").css("border", "1px solid rgb(170, 31, 37)");
            $("#cog").css("transform", "rotate(-90deg)");
        } else {
            $(".preferencePanel").css("max-height", "0vh");
            $(".preferencePanel").css("border", "none");
            $("#cog").css("transform", "rotate(0deg)");
        }
    });

    $(document).on('click', '.listPhrase', function(e) { listItemClicked(e.target.id); }); // list item click listener

    function updateLists() { // updates positive, negative and custom phrase lists from localStorage
        // get items from localStorage
        positivePhrases = JSON.parse(localStorage.getItem("storePos"));
        negativePhrases = JSON.parse(localStorage.getItem("storeNeg"));
        customPhrases = JSON.parse(localStorage.getItem("storeCus"));
        // set list div write areas
        var positiveListDiv = document.getElementById("positiveList").getElementsByTagName("p")[0];
        var negativeListDiv = document.getElementById("negativeList").getElementsByTagName("p")[0];
        var customListDiv = document.getElementById("customList").getElementsByTagName("p")[0];
        // reset list content
        positiveListDiv.innerHTML = "";
        negativeListDiv.innerHTML = "";
        customListDiv.innerHTML = "";
        // iterate through lists, add divs dynamically to represent list item
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

    function listItemClicked(id) { // handles removal of list item
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

    function removeFromList(phrase, listType) { // remove phrase from given list
        var currlist = JSON.parse(localStorage.getItem(listType));
        if (currlist == null) {
            console.log("List to remove from is null");
        } else if (currlist.includes(phrase)) {
            currlist.splice(currlist.indexOf(phrase), 1); // remove phrase
            localStorage.setItem(listType, JSON.stringify(currlist)); // update localStorage
        } else {
            console.log("Phrase doesn't exist in list");
        }
        updateLists(); // draw updated lists
    }

    function popupClicked(type) { // text selection popup clicked
        if (type == "tick") {
            if (stringValid(selection)) {
                if (customPhrases == null) { // add selection to custom list
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

    function suggClicked(e, div) { // suggestion clicked
        var clickedDivText = document.getElementById(div).textContent; // clicked div text
        if (stringValid(clickedDivText)) processOnChange(e, true, clickedDivText); // push suggestion to text box
        else console.log("Invalid string: " + clickedDivText);
    }

    function addEntry(storageName, entryValue) { // add phrase to localStorage list
        if (localStorage.getItem(storageName) == "") { 
            localStorage.setItem(storageName, JSON.stringify([entryValue])); // add phrase to localStorage if list is empty
        } else {
            var existingEntries = JSON.parse(localStorage.getItem(storageName)) || [];
            // Save allEntries back to local storage
            existingEntries.push(entryValue);
            localStorage.setItem(storageName, JSON.stringify(existingEntries)); // add phrase to existing entries, add updated entries to localStorage
        }
        updateLists(); // draw updated lists
    };

    function stringValid(str) { // various tests to define string validity
        if (str == null || str == "" || str == undefined || str == "no suggestion") return false;
        else return true;
    }

    function tickClicked(tick) { // add phrase tick clicked
        var positiveSuggestion;
        for (var i = 0; i < maxSuggestions; i++) {
            if (tick == "tck" + i && stringValid(sugs[i])) { 
                positiveSuggestion = sugs[i]; // set positive phrase text
                break;
            }
        }
        if (positivePhrases == null) { // add positive phrase if it doesn't already exist
            addEntry("storePos", positiveSuggestion);
        } else if (!positivePhrases.includes(positiveSuggestion)) {
            addEntry("storePos", positiveSuggestion);
        } else {
            console.log("Phrase already exists in positive list: " + positiveSuggestion);
        }
    }

    function crossClicked(cross) { // remove phrase cross clicked
        var negativeSuggestion;
        var negativeSuggestionIndex;
        for (var i = 0; i < maxSuggestions; i++) {
            if (cross == "crs" + i && stringValid(sugs[i])) {
                negativeSuggestion = sugs[i]; // set negative phrase text
                negativeSuggestionIndex = i; // set negative phrase index
                break;
            }
        }
        if (negativePhrases == null) { // add negative phrase if it doesn't already exist
            addEntry("storeNeg", negativeSuggestion);
        } else if (!negativePhrases.includes(negativeSuggestion)) {
            addEntry("storeNeg", negativeSuggestion);
        } else {
            console.log("Phrase already exists in negative list: " + negativeSuggestion);
        }
        sugs.splice(negativeSuggestionIndex, 1); // remove item from suggestion list
        setResults(sugs, state); // draw updated lists
    }

    function onTopKChange(topk) { // topk/maximum number of suggestions change
        document.getElementById("suggestions").innerHTML = ""; // reset suggestions
        for (var i = 0; i < topk; i++) { // for each suggestion
            // create flex-horizontal parent div
            var newHorizontalFlexDiv = document.createElement("div");
            newHorizontalFlexDiv.className = "flex-horizontal";
            // create tick image
            var newTickImage = document.createElement("img");
            newTickImage.src = "images/tick.png"; 
            newTickImage.className = "tick";
            newTickImage.id = "tick" + i;
            newTickImage.title = "Click to see this suggestion more";
            // create cross image
            var newCrossImage = document.createElement("img");
            newCrossImage.src = "images/cross.png"; 
            newCrossImage.className = "cross";
            newCrossImage.id = "cross" + i;
            newCrossImage.title = "Click to never see this suggestion";
            // create suggestion text
            var newSuggestionDiv = document.createElement("div");
            newSuggestionDiv.className = "result";
            newSuggestionDiv.id = "divResult" + i;
            newSuggestionDiv.title = "Click to add this suggestion to text box";
            // append tick, corss, suggestion to parent div
            document.getElementById("suggestions").appendChild(newHorizontalFlexDiv);
            newHorizontalFlexDiv.appendChild(newTickImage);
            newHorizontalFlexDiv.appendChild(newCrossImage);
            newHorizontalFlexDiv.appendChild(newSuggestionDiv);
        }
        defineOnClickListeners(); // create onClick listeners for new suggestion items
        // regenerate suggestions
        suggCount = 0;
        sugs = [];
        regenerateSuggestions();
    }

    function regenerateSuggestions() { // uses current input to regenerate suggestions
        var cursorLoc = inputElement.selectionStart; // current cursor location
        var input = $("#inputString").val(); // entire input text
        var beforeCursor = input.substring(0, cursorLoc); // all text before cursor location
        var currSentence = beforeCursor.split(/[.?!]\s|[\\\n\r\t]/).pop(); // all text between cursor location and last end-of-sentence character
        getSuggestions(currSentence, "", 0); // get suggestions for input
    }

    function setResult(s) { // writes given string (no suggestion) to result div
        $("#divResult0").css({"color" : "grey"}); 
        $("#divResult0").text(s); 
        $("#divResult0").prop("title", ""); 
        $("#divResult0").css({"margin-left" : "-2.5em"}); 
        for (var i = 1; i < maxSuggestions; i++) { // remove all suggestions apart from first
            $("#divResult" + i).text(""); 
        }
        renderTickCrossImages(); // draw tick and cross images for existing suggestion items
    }

    function setResults(sgs, st) { // writes given string to result div
        resetResults(); // remove all results
        state = st; // set global state to state parameter
        $("#divResult0").css({"margin-left" : "0"}); 
        if (sgs[0]) lastTopSuggestion = sgs[0]; // update lastTopSuggestion - used for macron/casing/punctuation replacement
        if (st == "pt") $("#divResult0").css({'color':'black'}); // set top suggestion to black if threshold exceeded
        else $("#divResult0").css({'color':'grey'}); 
        for (var i = 0; i < maxSuggestions; i++) {
            if (sgs[i]) {
                var lastSevenWords = sgs[i].split(" ").slice(-7).join(" "); // limits result output to seven words - this could be improved by lining up the first word with other suggestions'
                $("#divResult" + i).text(lastSevenWords);
            }
        }
        renderTickCrossImages(); // draw tick and cross images for existing suggestion items
    }

    function showImage(id) { document.getElementById(id).style.visibility = "visible"; } // shows given image

    function hideImage(id) { document.getElementById(id).style.visibility = "hidden"; } // hides given image

    function resetResults() { for (var i = 0; i < maxSuggestions; i++) $("#divResult" + i).text(""); } // removes all suggestion results

    function renderTickCrossImages() { // draws tick and cross images as necessary
        if ($("#divResult0").text() == "" || $("#divResult0").text() == "no suggestion") { // remove images if "no suggestion"
            hideImage("cross0"); 
            hideImage("tick0");
        } else {
            showImage("cross0");
            showImage("tick0");
        }
        for (var i = 1; i < maxSuggestions; i++) { // draw images if non-empty result exists
            if ($("#divResult" + i).text() == "") {
                hideImage("cross" + i);
                hideImage("tick" + i);
            } else {
                showImage("cross" + i);
                showImage("tick" + i);
            }
        }
    }
});