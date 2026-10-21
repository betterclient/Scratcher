import fs from 'node:fs';
import { createRequire } from 'node:module';
import VirtualMachine from 'scratch-vm';

const require = createRequire(import.meta.url);
const ScratchStorageRaw = require('scratch-storage');
const ScratchStorage = typeof ScratchStorageRaw === 'function'
    ? ScratchStorageRaw
    : (ScratchStorageRaw.ScratchStorage || ScratchStorageRaw.default || ScratchStorageRaw);

const [, , projectPath, maxWaitMs = '1000'] = process.argv;

if (!projectPath) {
    console.error('Missing .sb3 project path');
    process.exit(1);
}

let config = { asks: [] };
try {
    const stdin = fs.readFileSync(0, 'utf-8').trim();
    if (stdin) {
        config = JSON.parse(stdin);
    }
} catch {}

async function main() {
    const vm = new VirtualMachine();
    vm.attachStorage(new ScratchStorage());

    const logs = [];
    const questions = [];
    const askQueue = [...(config.asks || [])];
    let activeQuestion = null;

    vm.runtime.on('SAY', (target, type, text) => {
        if (!text || text === activeQuestion) return;
        logs.push(String(text));
    });

    const originalAsk = vm.runtime._primitives['sensing_askandwait'];
    vm.runtime._primitives['sensing_askandwait'] = function (args, util) {
        const questionText = String(args.QUESTION);
        questions.push(questionText);
        activeQuestion = questionText;

        const next = askQueue.shift();
        const answer = next?.answer !== undefined ? String(next.answer) : '';

        const promise = originalAsk.call(this, args, util);

        setTimeout(() => {
            vm.runtime.emit('ANSWER', answer);
            activeQuestion = null;
        }, 15);

        return promise;
    };

    const fileBuffer = fs.readFileSync(projectPath);
    await vm.loadProject(fileBuffer);

    vm.start();
    vm.greenFlag();

    const timeout = parseInt(maxWaitMs, 10);
    const start = Date.now();

    await new Promise((resolve) => {
        const timer = setInterval(() => {
            const hasActiveThreads = vm.runtime.threads.some((t) => !t.isDone);
            if (!hasActiveThreads || Date.now() - start >= timeout) {
                clearInterval(timer);
                resolve();
            }
        }, 15);
    });

    const variables = {};
    const lists = {};

    for (const target of vm.runtime.targets) {
        for (const variable of Object.values(target.variables)) {
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