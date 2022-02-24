# Te Reo Māori Auto Completion

## Requirements
To operate this tool, the host PC must have the following:
- A working Java installation
- A recent version of Greenstone 3's source distribution

## Installation
To install this tool, clone this repository to your local drive and move the java-autocomplete directory into greenstone3/ext/atea-nlp-tools/te-reo-autocomplete/. 

Then, source a dataset for the auto-completion tool to use as input.

Currently, the tool supports the following items as input:
- Reo Māori Twitter corpus (rmt) [generate your own here](). .csv, all columns removed except for date and tweet
- Any .txt file (txt) (at least 50,000 lines is recommended)
- The .json output (mbox) from the [email-retrieval tool](../email-retrieval/)

Move your dataset into the /corpus/ directory. Open /src/main/webapp/WEB-INF/web.xml.in and change the servlet init-parameter "dataType" to one of: rmt, txt or mbox to reflect the type of data you would like to use as input. The default value for this parameter is txt.

Change the servlet init-parameter "corpus" to match the directory of your chosen corpus.

Open a terminal within your greenstone directory. Run `ant start` to initialize the Tomcat servlet service. Move to the /java-autocomplete/ directory and run `ant install`. This will begin the tree indexing process. Once the tree is built, the application should be accessible on a browser at `localhost:8383/gs3-autocomplete`.

The log output of the program is visible at: greenstone3/packages/tomcat/logs/catalina.out.

# Troubleshooting
The majority of errors produced will be traceable through the catalina.out log file mentioned above. 

# Usage
### **Basic usage**
To use the auto-completion tool, begin writing in the text box. Suggestions will appear below the text box, with the top suggestion ranking the highest. If the suggestion is black, it can then be pushed to the textbox by pressing TAB. Grey suggestions are suggestions that have not yet passed the threshold. 

### **Preference Lists**
If you would like to see a phrase appear at the top more often, add it to the positive list by clicking the tick to the left of the suggestion.

If you would like to hide a phrase from the results, add it to the negative list by clicking the cross to the left of the suggestion.

To add a custom phrase that doesn't appear automatically, highlight/select the text using your mouse and click the tick in the popup window.

### **Option menu**
To view the options and lists, click the cog on the bottom right of the text box. Here, the number of suggestions can be changed with the drop down menu. The positive, negative and custom list contents can also be viewed here. Items in lists can be removed by clicking on the suggestion. This menu can be closed by clicking again on the cog.

## License
[GPL v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html)