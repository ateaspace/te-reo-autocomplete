# Sent Emails Retrieval Service for Gmail

## Requirements
To operate this tool, node.js must be installed.

## Installation
To install this tool, clone this repository to your local drive.

## Usage
To use this tool, you must first generate a credentials.json file that is linked to a Google Cloud Platform (GCP) Project. This ensures proper authentication before this tool gains access to your Gmail data. 

First, [create a new GCP project](https://developers.google.com/workspace/guides/create-project#create_a_new_google_cloud_platform_gcp_project) and [enable the Gmail API](https://developers.google.com/workspace/guides/create-project#enable-api). 
Then, [configure the OAuth consent screen](https://developers.google.com/workspace/guides/create-credentials#configure_the_oauth_consent_screen) and create [desktop application credentials](https://developers.google.com/workspace/guides/create-credentials#desktop). Rename the downloaded .JSON file to 'credentials.json' and move it to the root directory of this project.

In a terminal, run `node .` to execute the service. If this is your first time executing, it will prompt you to open a link. Open this and sign in using the account you would like this service to operate on. 

Copy the returned code and paste it back into the terminal. The service should run.

Note that the API may return an error: "Too many concurrent requests for user". Unfortunately this is unavoidable. The program will still successfully write an output file, it simply will be missing the oldest emails.

## License
[GPL v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html)
