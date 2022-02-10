$(document).ready(function(){

    var lastSent = "";
    var KEY_TAB = 9;
    var sugs = [];
    var state;
    var textareaX;
    var textareaY;
    var selection;
    var positivePhrases;
    var negativePhrases;
    var customPhrases;

    updateLists();

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
                // create get request with value of text field
                $.ajax({url: "pat", type: "get", dataType: "json", data: {inputString: currSentence}, success: function(result) { 
                    state = result.state;
                    sugs[0] = result.sg0; 
                    sugs[1] = result.sg1; 
                    sugs[2] = result.sg2;
                    setResults(sugs[0], sugs[1], sugs[2], state);
                    if (state == "ns") { // no suggestion
                        setResult("no suggestion");
                    } else if (state == "ft" || state == "pt") { // if suggestions exist
                        if (positivePhrases.length > 0) {
                            var changeMade = false;
                            for (i in sugs) {
                                for (j in positivePhrases) {
                                    if (sugs[i] == positivePhrases[j]) {
                                        var tmp = sugs[0]; // save top
                                        sugs[0] = sugs[i]; // set top to match
                                        sugs[i] = tmp; // set match to top (swap)
                                        changeMade = true;
                                        setResults(sugs[0], sugs[1], sugs[2], "pt");
                                    }
                                }
                            }
                            if (!changeMade) setResults(sugs[0], sugs[1], sugs[2], state);
                        } 
                        if (negativePhrases.length > 0) {
                            var changeMade = false;
                            for (i in sugs) {
                                for (j in negativePhrases) {
                                    if (sugs[i] == negativePhrases[j]) {
                                        sugs.splice(i, 1);
                                        changeMade = true;
                                    }
                                }
                            }
                            setResults(sugs[0], sugs[1], sugs[2], state);
                        }                       
                    } else {
                        console.log("given state does not exist: " + state);
                    }
                    if (customPhrases.length > 0) {
                        var changeMade = false;
                        for (j in customPhrases) {
                            if (sanitize(customPhrases[j]).startsWith(sanitize(currSentence))) {
                                // if (levenshtein(sanitize(customPhrases[j]), sanitize(currSentence)) < 5) {};
                                if ((sanitize(customPhrases[j]).length / 2) < sanitize(currSentence).length) {
                                    console.log("half of custPhrase length: " + sanitize(customPhrases[j]).length / 2);
                                    console.log("curr length: " + sanitize(currSentence).length);
                                    sugs.unshift(customPhrases[j]);
                                    sugs = sugs.slice(0, 3);
                                    setResults(sugs[0], sugs[1], sugs[2], "pt");
                                    changeMade = true;
                                }
                            }
                        }
                        if (!changeMade) setResults(sugs[0], sugs[1], sugs[2], state);
                    }   
                    lastSent = currSentence;
                }}); 
            }
        }
    }

    function sanitize(str) {
        return str.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
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

        var entireSelection = document.getSelection(),
            range = entireSelection.getRangeAt(0),
            clientRects = range.getClientRects();
        // console.log(clientRects);

        // console.log("clientRects left: " + clientRects[0].left);
        // console.log("clientRects top: " + clientRects[0].top);

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

    $(document).on('mousedown', function(e) {
        textareaX = e.pageX;
        textareaY = e.pageY;
        $("div.selectpopup").fadeOut(200);
    });

    $("#popuptick").click(function(){ popupClicked("tick") });
    $("#popupcross").click(function(){ popupClicked("cross") });

    $("#listbutton").click(function(){
        var button = document.getElementById("listbutton");
        if (button.textContent == "Show Lists") {
            $(".listContainer").fadeIn(150);
            button.textContent = "Hide Lists";
        } else {
            $(".listContainer").fadeOut(150);
            button.textContent = "Show Lists";
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

        for (idx in positivePhrases) {
            positiveListDiv.innerHTML += "<div class=\"listPhrase\" id=\"plist" + idx + "\">" + positivePhrases[idx] + "</div>";
        }
        for (idx in negativePhrases) {
            negativeListDiv.innerHTML += "<div class=\"listPhrase\" id=\"nlist" + idx + "\">" + negativePhrases[idx] + "</div>";
        }
        for (idx in customPhrases) {
            customListDiv.innerHTML += "<div class=\"listPhrase\" id=\"clist" + idx + "\">" + customPhrases[idx] + "</div>";
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
        updateLists();
    }

    function suggClicked(e, div) {
        var clickedDivText = document.getElementById(div).textContent;

        if (stringValid(clickedDivText)) {
            if (positivePhrases == null) { 
                addEntry("storePos", clickedDivText);
            } else if (!positivePhrases.includes(clickedDivText)){
                addEntry("storePos", clickedDivText);
            } else {
                console.log("Phrase already exists in positive list: " + clickedDivText);
            }
            processOnChange(e, true, clickedDivText);
        } else {
            console.log("Invalid string: " + clickedDivText);
        }
        updateLists();
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
    };

    function stringValid(str) {
        if (str == null || str == "" || str == undefined || str == "no suggestion") {
            return false;
        } else {
            return true;
        }
    }

    function crossClicked(cross) {
        var negativeSuggestion;
        if (cross == "crs0" && stringValid(sugs[0])) {
            negativeSuggestion = sugs[0];
        } else if (cross == "crs1" && stringValid(sugs[1])) {
            negativeSuggestion = sugs[1];
        } else if (cross == "crs2" && stringValid(sugs[2])) {
            negativeSuggestion = sugs[2];
        } else {
            console.log("given cross does not exist: " + cross + " or string is not valid: " + negativeSuggestion);
        }
        if (negativePhrases == null) {
            addEntry("storeNeg", negativeSuggestion);
        } else if (!negativePhrases.includes(negativeSuggestion)) {
            addEntry("storeNeg", negativeSuggestion);
        } else {
            console.log("Phrase already exists in negative list: " + negativeSuggestion);
        }
        updateLists();
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
        state = st;
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
});