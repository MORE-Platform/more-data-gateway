# Loadtest

## What
The loadtest loads unzipped cwa data (csv) in load it to the DSB. The result is a measurement file (csv, one line per datapoint).

## How

### Unpack CWA to csv
Use command line tool from https://github.com/digitalinteraction/openmovement/wiki/AX3-GUI .

### Setup
Loadtest used config.json to get properties. Copy config.tpl.json to config.json and change values
```json
{
  "system": {
    "baseUrl": "https://dsb.platform-test.more.redlink.io",
    "apiId": "2c938088835f068d0183601d64b50012", // required
    "apiKey": "d6950f4d-b26d-4e9f-bb63-0136582251a5" // required
  },
  "files": {
    "input": "./unpacked.csv", // can be also "FAKE"
    "output": "200b-20w.csv"
  },
  "options": {
    "bulkSize": 50,
    "parallelWorkers": 30,
    "maxDataPoints": 200
  }
}
```

### Run
`npm install && npm run loadtest`

*Attention*: Datapoint Ids are linenumbers of datapoints, so loadtests override each other if same study is used for storing.

## Result
A csv file with datapoints times lines.
```
BulkId,Startet (ms),Finished (ms)
```
