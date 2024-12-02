import process from 'node:process';
import {Client} from "node-rest-client";
import {v4 as uuidv4} from 'uuid';

const baseUrl = (process.env.BASE_URL ?? "http://localhost:8085")
    .replace(/\/*$/, '')
const endpoint = `${baseUrl}/api/v1/external/bulk`;
const authToken = process.env.MORE_TOKEN
if (!authToken) {
    console.error("Missing auth token");
    process.exit(1);
}
console.log(endpoint);

const maxBulkSize = 1000
let _bulkSize = parseInt(process.env.BULK_SIZE ?? "100")
if (_bulkSize > maxBulkSize) {
    console.warn(`Max bulk-size exceeded, trunk to ${maxBulkSize}`);
}
const bulkSize = Math.min(_bulkSize, maxBulkSize);

console.debug("Args:", process.argv.slice(2))
const argv = process.argv.slice(2)
if (argv.indexOf('--help') !== -1) {
    console.info("Params: " + [
        '[<participantId>]', '[<dataPoints>]'].join(' '))
    process.exit(0)
}

const participantId = process.argv[2] ?? "1"
const dataPoints = parseInt(process.argv[3] ?? "1000")

const client = new Client()

const bulk = {
    participantId: `${participantId}`,
}

const iterations = Math.ceil(dataPoints / bulkSize)
const baseDate = new Date().getTime() //- iterations * 60 + 1000
for (let i = 0; i < iterations; i++) {
    const id = uuidv4()
    const date = baseDate + i * 60 * 1000
    const c = i === (iterations-1) ? (dataPoints % bulkSize) : bulkSize
    if (c === 0) break

    const dataBulk = {
        ...bulk,
        dataPoints: Array(c)
            .fill(0)
            .map((v, x) => {
                return {
                    dataId: (`${id}_${x}`),
                    dataValue: {
                        x,
                        y: -x,
                        z: Math.random() * bulkSize,
                    },
                    timestamp: new Date(date + x).toISOString(),
                }
            }),
    };

    await client.post(endpoint,
        {
            headers: {
                "Content-Type": "application/json",
                "More-Api-Token": authToken,
            },
            data: dataBulk,
        },
        (data, response) => {
            if (response.status < 300) {
                console.info(`Sent Bulk ${i} of ${iterations}`, data)
            } else if (response.statusCode >= 400) {
                console.error(`Sending data failed: ${response.statusCode} - ${response.statusMessage}`, data);
                process.exit(2);
            }
        })
        .on("error", (err) => {
            console.error(`Received ${err}`);
            process.exit(2);
        });
}



