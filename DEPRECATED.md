# Address Lookup Frontend

[![Build Status](https://travis-ci.org/hmrc/address-lookup-frontend.svg)](https://travis-ci.org/hmrc/address-lookup-frontend-new) [ ![Download](https://api.bintray.com/packages/hmrc/releases/address-lookup-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/address-lookup-frontend/_latestVersion)

This microservice provides a user interface for entering and editing addresses. Assistance is provided to the end-user for looking up their address from a database (via the backend service [address-lookup](https://github.com/hmrc/address-lookup)).

Initially, the use-case covers only UK addresses. BFPO addresses might be added soon. The roadmap includes support for international addresses.

## Functional Overview

### Summary

During the utilization of `address-lookup-frontend`, four parties are involved:

* A frontend service in the tax platform (the **"calling service"** here).
* The user-agent (i.e. web browser) and the user who operates it (the **"user"** here).
* The `address-lookup-frontend` (the **"frontend"** here).
* The backend `address-lookup`, containing large national datasets of addresses (the **"backend"** here).

The integration process from the perspective of the **calling service** consists of the following steps:

* _Initialize_ a **journey** by issuing a request to `POST /api/init` where the message body is a **journey configuration** JSON message (see below). You should receive a `202 Accepted` response with a `Location` header the value of which is the **"on ramp"** URL to which the **"user"** should be redirected.
* _Redirect_ the **"user"** to the **"on ramp"** URL.
* The **"user"** completes the journey, following which they will be redirected to the **"off ramp"** URL (which is configured as part of the journey) with an appended `id=:addressId` URL parameter.
* Using the value of the `id` parameter, you can retrieve the user's confirmed address as JSON by issuing a request to `GET /api/confirmed?id=:addressId`. 

![Address Lookup Frontend journey](https://raw.githubusercontent.com/hmrc/address-lookup-frontend/master/docs/design.png)

### Initializing a Journey

The first action by any **calling service** must be to **"initialize"** an address lookup **journey** in order to obtain an "on ramp" URL to which the **"user"** is then redirected. 

Initialization generates an ID which is utilized both to facilitate subsequent retrieval of the **"user's"** confirmed address *and* to prevent arbitrary access to and potential abuse of `address-lookup-frontend` by malicious **"users"**.

An endpoint is provided for initialization:

URL:

* `/api/init`

Methods:

* `POST`

Headers:

* `User-Agent` (required): string

Message Body:

* A **journey configuration** message in `application/json` format (see details of the JSON format below)

Status Codes:

* 202 Accepted: when initialization was successful
* 500 Internal Server Error: when, for any (hopefully transient) internal reason, the journey could not be initialized 

Response:

* No content
* The `Location` header will specify an **"on ramp"** URL, appropriate for the journey, to which the user should be redirected. Currently, the journey has a TTL of **60 minutes**.

### Configuring a Journey

The `address-lookup-frontend` allows the **"calling service"** to customize many aspects of the **"user's"** journey and the appearance of the **"frontend"** user interface. Journey configuration is supplied as a JSON object in the body of the request to `POST /api/init` (see above).

It is **not** necessary to specify values for all configurable properties. _Only supply a value for properties where it is either required or you need to override the default_. Wherever possible, sensible defaults have been provided. The default values are indicated in the table detailing the options below.

#### Configuration JSON Format

Journey configuration is supplied as a JSON object in the body of the request to `POST /api/init`.

It is **not** necessary to specify values for all configurable properties. _Only supply a value for properties where it is either required or you need to override the default_. Wherever possible, sensible defaults have been provided. The default values are indicated in the table detailing the options below.

```json
{
  "continueUrl" : "/lookup-address/confirmed",
  "homeNavHref" : "http://www.hmrc.gov.uk/",
  "navTitle" : "Address Lookup",
  "showPhaseBanner" : false,
  "alphaPhase" : false,
  "phaseFeedbackLink" : "/help/alpha",
  "phaseBannerHtml" : "This is a new service – your <a href='/help/alpha'>feedback</a> will help us to improve it.",
  "showBackButtons": false,
  "includeHMRCBranding" : true,
  "deskProServiceName" : "",
  "lookupPage" : {
    "title" : "Find the address",
    "heading" : "Find the address",
    "filterLabel" : "Property name or number",
    "postcodeLabel" : "UK Postcode",
    "submitLabel" : "Find address",
    "noResultsFoundMessage" : "Sorry, we couldn't find anything for that postcode.",
    "resultLimitExceededMessage" : "There were too many results. Please add additional details to limit the number of results.",
    "manualAddressLinkText" : "The address doesn't have a UK postcode"
  },
  "selectPage" : {
    "title" : "Choose the address",
    "heading" : "Choose the address",
    "proposalListLabel" : "Please select one of the following addresses",
    "submitLabel" : "Continue",
    "proposalListLimit" : 50,
    "showSearchAgainLink" : false,
    "searchAgainLinkText" : "Search again",
    "editAddressLinkText" : "Enter address manually"
  },
  "confirmPage" : {
    "title" : "Confirm the address",
    "heading" : "Review and confirm",
    "infoSubheading" : "Your selected address",
    "infoMessage" : "This is how your address will look. Please double-check it and, if accurate, click on the <kbd>Confirm</kbd> button.",
    "submitLabel" : "Confirm and continue",
    "showSearchAgainLink" : false,
    "searchAgainLinkText" : "Search again",
    "changeLinkText" : "Edit address",
    "showConfirmChangeText" : true,
    "confirmChangeText" : "By confirming this change, you agree that the information you have given is complete and correct."
  },
  "editPage" : {
    "title" : "Enter the address",
    "heading" : "Enter the address",
    "line1Label" : "Address line 1",
    "line2Label" : "Address line 2",
    "line3Label" : "Address line 3",
    "townLabel" : "Town/City",
    "postcodeLabel" : "Postal code (optional)",
    "countryLabel" : "Country",
    "submitLabel" : "Continue"
  },
  "timeout" : {
    "timeoutAmount" : 900,
    "timeoutUrl" : "http://service/timeout-uri"
  },
  "ukMode": false
}
```
#### Test Endpoint for journey setup
* `/lookup-address/test-only/test-setup` (GET)

#### Top-level configuration JSON object

|Field name|Description|Optional/Required|Type|Default value|
|----------|-----------|-----------------|----|-------------|
|`continueUrl`|the "off ramp" URL for a user journey|**Required**|String|N/A|
|`homeNavHref`|value of the link href attribute for the GDS "home" link|Optional|String|`"http://www.hmrc.gov.uk/"`|
|`navTitle`|the main masthead heading text|Optional|String|`"Address Lookup"`|
|`showPhaseBanner`|whether or not to show a phase banner (if `showPhaseBanner == true && alphaPhase == false`, shows "beta")|Optional|Boolean|`false`|
|`alphaPhase`|if `showPhaseBanner = true && alphaPhase == true`, will show "alpha" phase banner|Optional|Boolean|`false`|
|`phaseFeedbackLink`|link to provide a user feedback link for phase banner|Optional|String|`"/help/alpha"`|
|`phaseBannerHtml`|text (allows HTML tags) for phase banner|Optional|String|`"This is a new service – your <a href='/help/alpha'>feedback</a> will help us to improve it."`"
|`showBackButtons`|whether or not to show back buttons on user journey wizard forms|Optional|Boolean|`false`|
|`includeHMRCBranding`|whether or not to use HMRC branding|Optional|Boolean|`true`|
|`deskProServiceName`|name of your service in deskpro. Used when constructing the "report a problem" link. Defaults to None.|Optional|String|`None`|
|`allowedCountryCodes`|country codes list allowed in manual edit dropdown|Optional|List of Strings|All countries|
|`ukMode`|enable uk only Lookup and Edit mode|Optional|Boolean|`None`|
#### Lookup page configuration JSON object

Configuration of the "lookup" page, in which user searches for address using filter + postcode. The lookup page configuration is a nested JSON object inside the journey configuration under the `lookupPage` property.

|Field name|Description|Optional/Required|Type|Default value|
|----------|-----------|-----------------|----|-------------|
|`title`|the `html->head->title` text|Optional|String|`"Find the address"`|
|`heading`|the heading to display above the lookup form|Optional|String|`"Find the address"`|
|`filterLabel`|the input label for the "filter" field|Optional|String|`"Property name or number"`|
|`postcodeLabel`|the input label for the "postcode" field|Optional|String|`"UK postcode"`|
|`submitLabel`|the submit button text (proceeds to the "select" page)|Optional|String|`"Search for the address"`|
|`noResultsFoundMessage`|message to display in infobox above lookup form when no results were found|Optional|String|`"Sorry, we couldn't find anything for that postcode."`|
|`resultLimitExceededMessage`|message to display in infobox above lookup form when too many results were found (see selectPage.proposalListLimit)|Optional|String|`"There were too many results. Please add additional details to limit the number of results."`|
|`manualAddressLinkText`|Text to use for link to manual address entry form|Optional|String|`"The address does not have a UK postcode"`|

#### Select page configuration JSON object

Configuration of the "select" page, in which user chooses an address from a list of search results. The select page configuration is a nested JSON object inside the journey configuration under the `selectPage` property.

|Field name|Description|Optional/Required|Type|Default value|
|----------|-----------|-----------------|----|-------------|
|`title`|the `html->head->title` text|Optional|String|`"Choose the address"`|
|`heading`|the heading to display above the list of results|Optional|String|`"Choose the address"`|
|`proposalListLabel`|the radio group label for the list of results|Optional|String|`"Please select one of the following addresses"`|
|`submitLabel`|the submit button text (proceeds to the "confirm" page)|Optional|String|`"Continue"`|
|`proposalListLimit`|maximum number of results to display (when exceeded, will return user to "lookup" page)|Optional|Integer|`nothing`|
|`showSearchAgainLink`|Whether or not to show "search again" link back to lookup page|Optional|Boolean|`false`|
|`searchAgainLinkText`|Link text to use when 'showSearchAgainLink' is true|Optional|String|`"Search again"`|
|`editAddressLinkText`|Link text to use for the "edit adddress" link|Optional|String|`"Enter the address manually"`|

#### Confirm page configuration JSON object

Configuration of the "confirm" page, in which the user is requested to confirm a "finalized" form for their address. The confirm page configuration is a nested JSON object inside the journey configuration under the `confirmPage` property.

|Field name|Description|Optional/Required|Type|Default value|
|----------|-----------|-----------------|----|-------------|
|`title`|the html->head->title text|Optional|String|`"Confirm the address"`|
|`heading`|the main heading to display on the page|Optional|String|`"Review and confirm"`|
|`infoSubheading`|a subheading to display above the "finalized" address|Optional|String|`"Your selected address"`|
|`infoMessage`|an explanatory message to display below the subheading to clarify what we are asking of the user (accepts HTML)|Optional|String|`"This is how your address will look. Please double-check it and, if accurate, click on the <kbd>Confirm</kbd> button."`|
|`submitLabel`|the submit button text (will result in them being redirected to the "off ramp" URL (see continueUrl)|Optional|String|`"Confirm and continue"`|
|`showSearchAgainLink`|Whether or not to show "search again" link back to lookup page|Optional|Boolean|`false`|
|`searchAgainLinkText`|Link text to use when 'showSearchAgainLink' is true|Optional|String|`"Search again"`|
|`showChangeLink`|Whether or not to show "Edit address" link back to Edit page|Optional|Boolean|`true`|
|`changeLinkText`|Link text to use for the "edit adddress" link|Optional|String|`"Edit this address"`|
|`showConfirmChangeText`|Whether or not to show "confirmChangeText" displayed above the submit button|Optional|Boolean|`false`|
|`confirmChangeText`|Text displayed above the submit button when 'showConfirmChangeText' is true|Optional|String|`"By confirming this change, you agree that the information you have given is complete and correct."`|

#### Edit page configuration JSON object

Configuration of the "edit" page, in which the user is permitted to manually enter or modify a selected address. The confirm page configuration is a nested JSON object inside the journey configuration under the `editPage` property.

|Field name|Description|Optional/Required|Type|Default value|
|----------|-----------|-----------------|----|-------------|
|`title`|the html->head->title text|Optional|String|`"Enter the address"`|
|`heading`|the heading to display above the edit form|Optional|String|`"Enter the address"`|
|`line1Label`|the input label for the "line1" field (commonly expected to be street number and name); a REQUIRED field|Optional|String|`"Address line 1"`|
|`line2Label`|the input label for the "line2" field; an optional field|Optional|String|`"Address line 2"`|
|`line3Label`|the input label for the "line3" field; an optional field|Optional|String|`"Address line 3"`|
|`townLabel`|the input label for the "town" field; a REQUIRED field|Optional|String|`"Town/City"`|
|`postcodeLabel`|the input label for the "postcode" field; a REQUIRED field|Optional|String|`"Postal code (optional)"`|
|`countryLabel`|the input label for the "country" drop-down; an optional field (defaults to UK)|Optional|String|`"Country"`|
|`submitLabel`|the submit button text (proceeds to the "confirm" page)|Optional|String|`"Continue"`|

#### Timeout Configuration JSON object (Optional)

Configuration of the timeout popup in which user is shown a popup allowing them to extend their session before it times out. The timeout configuration is a nested JSON object inside the journey configuration under the `timeout` property.

|Field name|Description|Optional/Required|Type|Default value|
|----------|-----------|-----------------|----|-------------|
|`timeoutAmount`|the duration of session timeout in seconds (between 120 and 999999999 seconds)|Required|Int|N/A|
|`timeoutUrl`|the url to be redirected to on session timeout|Required|String|N/A|

Additional configuration options may be introduced in future; for instance to prohibit "edit", to bypass "lookup", or to modify validation procedures for international or BFPO addresses. However, the design intent is that **all** configuration options should **always** have a default value. Consequently, **"calling services"** should only ever need to provide overrides to specific keys, rather than re-configuring or duplicating the entire journey for each scenario.

#### ukMode (Optional)

When enabled:

Lookup returns Only UK Addresses; 1 link on Lookup Page is overridden; Edit Address Mode removes option to change country (Defaults to United Kingdom) and omits postcode field.

### Obtaining the Confirmed Address

Once the user has completed the address lookup journey, they will be redirected to the **off ramp** URL specified in the **journey configuration**. An `id` parameter will be appended to the **off ramp** URL. **Calling services** may use the value of this parameter to obtain the **user's** confirmed address.

URL:

* `/api/confirmed`

Example URLs:

* `/api/confirmed?id=ID_VALUE_FROM_APPENDED_OFF_RAMP_URL_ID_PARAMETER_HERE`

Methods:

* `GET`

Headers:

* `User-Agent` (required): string

Message Body:

* None

Status Codes:

* 200 Ok: when a confirmed address was successfully obtained for the given `ID`
* 404 Not Found: when no confirmed address was found for the given `ID`
* 500 Internal Server Error: when, for any (hopefully transient) internal reason, the journey data corresponding to the ID could not be obtained 

Response:

* An `application/json` message which describes a **confirmed address** (see below)

#### Confirmed Address JSON Format

TODO

### Running the Application

TODO

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")