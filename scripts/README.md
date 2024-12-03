# More Data-Scripts

## external-api

Generate random data for observations using the external bulk-api.

**TL;DR**: You need a _running study_ with a _running observation_ and an _active participant_.
Create an _integration_ for the observation to get the `MORE_TOKEN`.

```shell
export BASE_URL=http://localhost:8085/
export MORE_TOKEN="*****"
export BULK_SIZE=500
npm run external-api <participant-id> <datapoint-count>
```

