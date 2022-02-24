const fs = require('fs');
const readline = require('readline');
const output_path = 'output.json';
const num_results = 1000;
var output = [];
var emailCount = 0;

const {
  google
} = require('googleapis');

// If modifying these scopes, delete token.json.
const SCOPES = ['https://www.googleapis.com/auth/gmail.readonly'];
// The file token.json stores the user's access and refresh tokens, and is
// created automatically when the authorization flow completes for the first
// time.
const TOKEN_PATH = 'token.json';

// Load client secrets from a local file.
fs.readFile('credentials.json', (err, content) => {
  if (err) return console.log('Error loading client secret file:', err);
  // Authorize a client with credentials, then call the Gmail API.
  authorize(JSON.parse(content), listEmails);
});

/**
 * Create an OAuth2 client with the given credentials, and then execute the
 * given callback function.
 * @param {Object} credentials The authorization client credentials.
 * @param {function} callback The callback to call with the authorized client.
 */
function authorize(credentials, callback) {
  const {
    client_secret,
    client_id,
    redirect_uris
  } = credentials.installed;
  const oAuth2Client = new google.auth.OAuth2(
    client_id, client_secret, redirect_uris[0]);

  // Check if we have previously stored a token.
  fs.readFile(TOKEN_PATH, (err, token) => {
    if (err) return getNewToken(oAuth2Client, callback);
    oAuth2Client.setCredentials(JSON.parse(token));
    callback(oAuth2Client);
  });
}

/**
 * Get and store new token after prompting for user authorization, and then
 * execute the given callback with the authorized OAuth2 client.
 * @param {google.auth.OAuth2} oAuth2Client The OAuth2 client to get token for.
 * @param {getEventsCallback} callback The callback for the authorized client.
 */
function getNewToken(oAuth2Client, callback) {
  const authUrl = oAuth2Client.generateAuthUrl({
    access_type: 'offline',
    scope: SCOPES,
  });
  console.log('Authorize this app by visiting this url:', authUrl);
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });
  rl.question('Enter the code from that page here: ', (code) => {
    rl.close();
    oAuth2Client.getToken(code, (err, token) => {
      if (err) return console.error('Error retrieving access token', err);
      oAuth2Client.setCredentials(token);
      // Store the token to disk for later program executions
      fs.writeFile(TOKEN_PATH, JSON.stringify(token), (err) => {
        if (err) return console.error(err);
        console.log('Token stored to', TOKEN_PATH);
      });
      callback(oAuth2Client);
    });
  });
}

/**
 * Lists the labels in the user's account.
 *
 * @param {google.auth.OAuth2} auth An authorized OAuth2 client.
 */
function listEmails(auth) {
  var sentMessagesIDs = [];

  const gmail = google.gmail({
    version: 'v1',
    auth
  });

  try {
    if (fs.existsSync(output_path))
      fs.unlinkSync(output_path) // remove old file
  } catch(err) {
    console.error(err)
  }

  // gmail.users.getProfile({
  //   auth: auth,
  //   userId: 'me'
  // }, function (err, res) {500esTotal);
  //   }
  // });

  function getMessageIDs(nextPageToken = '') {
    let options = {
      userId: 'me',
      q: 'in:sent',
      maxResults: num_results,
      pageToken: nextPageToken
    }
    gmail.users.messages.list(options, (err, res) => {
      if (err) return console.log('messages.list returned an error: ' + err);
      messages = res.data.messages;
      //pageToken = res.data.nextPageToken;
      if (messages.length) {
          messages.forEach((msg) => {
            emailCount++;
          //console.log(`- ${msg.id}`);
          //sentMessagesIDs.push(msg.id);
          getMessageContents(msg.id);
        });
      } else {
        console.log('No messages found');
      }
      if (res.data.nextPageToken) {
        console.log('fetching email ids for pageToken: ', res.data.nextPageToken);
        getMessageIDs(res.data.nextPageToken);
      } else {
        setTimeout(function() {
          saveJSON(output_path, output);
        }, 4000);
      }
    });
  }

  function getMessageContents(mid) {
    //console.log('retrieving latest ' + num_results + ' emails...');
    gmail.users.messages.get({  
      userId: 'me',
      id: mid
    }, (err, res) => {
      if (err) return console.log('The API returned an error: ' + err);
      try {
        var emailBody;
        if (res.data.payload.parts[0].body.data != undefined) {
          emailBody = res.data.payload.parts[0].body.data;
        } 
        //console.log(res.data.payload);
        var emailDate, emailTo, emailFrom = null;
        res.data.payload.headers.forEach(ele => {
          if (ele.name === "Date") {
            emailDate = new Date(ele.value).toLocaleDateString();
          } else if (ele.name === "To") { // remove text around <> brackets, leaving email
            emailTo = ele.value.replace( /(^.*\<|\>.*$)/g, '' ); 
          } else if (ele.name === "From") {
            emailFrom = ele.value.replace( /(^.*\<|\>.*$)/g, '' );
          }
        });
        var emailContents = processEmailBody(emailBody);
        var element = { body: emailContents, date: emailDate, to: emailTo, from: emailFrom };
        output.push(element);
      } catch (error) {
        //console.log(error);
      }
    });
  }
  getMessageIDs();
}

// converts body text from base64, cleans text
function processEmailBody(body) {
  var out = Buffer.from(body, 'base64').toString();
  out = out.split(/[<>]+/g).join(''); // remove < and > characters
  out = out.replace(/\s\s+/g, ' '); // replace two or more spaces with one
  out = out.replace(/\-\-+/g, '-'); // replace two or more hyphens with one
  out = out.replace(/(On)\s.{0,200}(wrote:)/g, ''); // remove 'On ... wrote:'
  out = out.replace(/(\*From:\*).*(\*Subject:\*)/g, ''); // remove '*From:* etc.' conversation headers
  out = out.replace(/(From:).*(Subject:)/g, ''); // remove '*From:* etc.' conversation headers
  out = out.replace(/(\*From:\*).*(\*To:\*)/g, ''); // remove '*From:* etc.' conversation headers
  out = out.replace(/https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&//=]*)/g, ''); // remove URLs https://stackoverflow.com/questions/3809401/what-is-a-good-regular-expression-to-match-a-url
  out = out.replace(/(www)\..{0,100}(\.nz)|(\.com)/g,''); // remove URLs not caught by above expression
  out = out.replace(/\-{8,12}\s(Forwarded).*(Subject:)/g,''); // remove ----- Forwarded Message ----- etc.
  return out;
}

// saves js object as JSON when given a file name and object
function saveJSON(fileName, obj) {
  const uniqueObj = [...new Map(obj.map(v => [v.body, v])).values()];
  fs.writeFile(fileName, JSON.stringify(uniqueObj), (err) => {
    if (err) {
        throw err;
    }
    console.log("\ntotal emails retrieved: ", emailCount);
    console.log("total saved emails: ", countProperties(uniqueObj));
    console.log("email data save in output.json");
  });
}

function countProperties(obj) {
  var count = 0;
  for(var prop in obj) {
    if(prop)
      ++count;
  }
  return count;
}