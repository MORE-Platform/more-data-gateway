import pLimit from 'p-limit';
import countLines from 'linecount/promise.js';
import lineByLine from 'n-readlines';
import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';
import {createObjectCsvWriter} from 'csv-writer'
import { readFileSync } from "fs";
const config = JSON.parse(readFileSync("./config.json"));

function checkRequired(folder, property, _default) {
    const value = (config[folder] ? config[folder][property] : undefined) || _default;
    if(!value) {
        console.error(`Property ${folder}.${property} is required`);
        process.exit(0);
    }
    console.log(`Property ${folder}.${property}: ${value}`)
    return value;
}

const baseUrl = checkRequired('system','baseUrl', "https://data.platform-test.more.redlink.io")
const endPoint = checkRequired('system', 'endPoint', 'bulk')
const apiId = checkRequired('system','apiId')
const apiKey = checkRequired('system','apiKey')
const file = checkRequired('files','input')
const useFakeData = file === "FAKE"
const bulkSize = checkRequired('options', 'bulkSize', 100);
const parallelWorkers = checkRequired('options', 'parallelWorkers', 2);
const maxDataPoints = checkRequired('options', 'maxDataPoints', Number.MAX_VALUE);
const output = checkRequired('files','output',`./out/${maxDataPoints === Number.MAX_VALUE ? 'all' : maxDataPoints}p-${bulkSize}b-${parallelWorkers}w_${endPoint}.csv`)

let liner;
if(!useFakeData) {
    liner = new lineByLine(file);
    liner.next();
}

const url = `${baseUrl}/api/v1/data/${endPoint}`
const auth = {
    username: apiId,
    password: apiKey
}

function readLines(id, size) {
    const lines = [];
    for (let i = 0; i < size; i++) {
        const line = liner.next();
        if(line) {
            lines.push({line: line.toString('ascii'), id:((id*bulkSize)+i).toString()});
        } else {
            break;
        }
    }
    return lines;
}

function createBulk(bulkId, lines) {
    const dataPoints =  lines.map(line => {
        const lineSplit = line.line.split(',');
        return {dataId: line.id, moduleId: 'm1', moduleType: 'ACCELEROMETER', timestamp: new Date(lineSplit[0].replace(/\[.+/,'')).toISOString(), dataValue:{acc_float: parseFloat(lineSplit[1])}}
    }).filter(dataPoint => {
        return !isNaN(dataPoint.dataValue.acc_float);
    });

    return {bulkId, dataPoints};
}

const dataBuilders = [
    (dataId) => ({dataId, moduleId: 'm1', observationId: '1', observationType: 'acc-mobile-observation', timestamp: new Date().toISOString(), dataValue:{x: Math.random()*10,y: Math.random()*10,z: Math.random()*10}}),
    (dataId) => ({dataId, moduleId: 'm2', observationId: '2', observationType: 'gps-mobile-observation', timestamp: new Date().toISOString(), dataValue:{latitude: Math.random()*50,longitude: Math.random()*50,altitude: Math.floor(Math.random()*8000)}})
]

function createFakeBulk(bulkId, count) {
    const dataPoints = [];
    for(let i = 0; i < bulkSize; i++) {
        const randomDataBuilder = dataBuilders[Math.floor(Math.random() * dataBuilders.length)];

        dataPoints.push(randomDataBuilder.apply(this, [uuidv4()]));
    }
    return {bulkId, dataPoints};
}

(async () => {
    // get number of bulks
    const lineCount = useFakeData ? maxDataPoints : Math.min(await countLines(file), maxDataPoints);
    const bulkNum = Math.ceil(lineCount/bulkSize);
    const start_time = new Date();
    const bulkArray = Array.from(Array(bulkNum).keys())
    const worker = pLimit(parallelWorkers);

    const start = process.hrtime();

    function toMillis(hrt) {
        return Math.round(hrt[0] * 1000 + hrt[1] / 1000000);
    }

    function sendBulk(bulkId) {
        return new Promise((resolve) => {
            let bulk;
            if(useFakeData) {
                bulk = createFakeBulk(bulkId.toString(), bulkSize);
            } else {
                const lines = readLines(bulkId, bulkSize);
                bulk = createBulk(bulkId.toString(), lines);
            }
            const startBulk = process.hrtime(start)
            console.log(`send bulk ${bulkId}`)
            //console.log(JSON.stringify(bulk, null, 2));
            axios.post(url, bulk,{auth}).then(() => {
                resolve({
                    bulkId,
                    startedAt: toMillis(startBulk),
                    finishedAt: toMillis(process.hrtime(start))
                });
            }).catch((error) => {
                console.error(bulkId, error.message);
                process.exit(0);
            });
        })
    }

    let promises = bulkArray.map(bulkId => {
        return worker(() => sendBulk(bulkId));
    });
    // Only three promises are run at once (as defined above)
    const result = await Promise.all(promises);

    console.log(`Write csv file to ${output}`)

    const csvWriter = createObjectCsvWriter({
        path: output,
        header: [
            {id: 'bulkId', title: 'BulkId'},
            {id: 'startedAt', title: 'Startet (ms)'},
            {id: 'finishedAt', title: 'Finished (ms)'}
        ]
    });

    const end_time = new Date();
    console.log(`start time ${start_time}`);
    console.log(`end time ${end_time}`);
    console.log(`time taken ${(end_time - start_time)/60000} minutes`);
    await csvWriter.writeRecords(result)
})();
