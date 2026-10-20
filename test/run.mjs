import fs from 'node:fs';
import { createRequire } from 'node:module';
import VirtualMachine from 'scratch-vm';

const require = createRequire(import.meta.url);
const ScratchStorageRaw = require('scratch-storage');
const ScratchStorage = typeof ScratchStorageRaw === 'function'
    ? ScratchStorageRaw
    : (ScratchStorageRaw.ScratchStorage || ScratchStorageRaw.default || ScratchStorageRaw);

const [,, projectPath, maxWaitMs = "1000"] = process.argv;

if (!projectPath) {
    console.error("Missing .sb3 project path");
    process.exit(1);
}

let config = { asks: [] };
try {
    const stdin = fs.readFileSync(0, 'utf-8').trim();
    if (stdin) {
        config = JSON.parse(stdin);
    }
} catch (_) {}

async function main() {
    const vm = new VirtualMachine();
    vm.attachStorage(new ScratchStorage());

    const logs = [];
    const questions = [];
    const askQueue = [...(config.asks || [])];

    vm.runtime.on('SAY', (target, type, text) => {
        if (text !== null && text !== undefined && text !== '') {
            logs.push(String(text));
        }
    });

    vm.runtime.on('QUESTION', (question) => {
        const qText = question !== null && question !== undefined ? String(question) : '';
        questions.push(qText);

        let answer = '';
        if (askQueue.length > 0) {
            const next = askQueue.shift();
            answer = next.answer !== undefined ? String(next.answer) : '';
        }

        setTimeout(() => {
            vm.runtime.emit('ANSWER', answer);
        }, 10);
    });

    const fileBuffer = fs.readFileSync(projectPath);
    await vm.loadProject(fileBuffer);

    vm.start();
    vm.greenFlag();

    const timeout = parseInt(maxWaitMs, 10);
    const start = Date.now();

    await new Promise((resolve) => {
        const timer = setInterval(() => {
            const activeThreads = vm.runtime.threads.filter((t) => !t.isDone);
            if (activeThreads.length === 0 || Date.now() - start >= timeout) {
                clearInterval(timer);
                resolve();
            }
        }, 15);
    });

    const variables = {};
    const lists = {};

    for (const target of vm.runtime.targets) {
        for (const [, variable] of Object.entries(target.variables)) {
            if (variable.type === 'list') {
                lists[variable.name] = variable.value;
            } else {
                variables[variable.name] = variable.value;
            }
        }
    }

    vm.quit();

    console.log(JSON.stringify({ variables, lists, logs, questions }));
    process.exit(0);
}

main().catch((err) => {
    console.error(err);
    process.exit(1);
});