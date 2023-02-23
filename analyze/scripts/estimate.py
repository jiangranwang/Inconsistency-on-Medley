import json
import time

import matplotlib.pyplot as plt
import numpy as np
import os


numNodes = [32, 64, 128, 256]
sampleSizes = [10, 15, 20]
numSample = 10


def estimate_func(numNodes, dropRate, numSample, sampleSize, endTime=10000):
    inDir = '../bin/membership'
    outDir = f'result/{numNodes}/{dropRate}'
    suspectFn = f'{outDir}/suspect_{sampleSize}_{numSample}.txt'
    estimateFn = f'{outDir}/estimate_{sampleSize}_{numSample}.txt'
    suspects = [0 for _ in range(numNodes)]
    isFirst = True
    for fn in [suspectFn, estimateFn]:
        if os.path.exists(fn):
            os.remove(fn)
    if not os.path.isdir(outDir):
        os.makedirs(outDir)

    start = time.time()
    while time.time() - start < endTime:
        time.sleep(1)
        missing2node = [set() for _ in range(numNodes)]
        node2missing = [set() for _ in range(numNodes)]
        curr = [-v for v in suspects]
        for i in range(numNodes):
            while True:
                try:
                    with open(f'{inDir}/{i}.json', 'r') as f:
                        obj = json.load(f)
                        for key in obj['suspects']:
                            curr[int(key)] += int(obj['suspects'][key])
                        missing = set([i for i in range(numNodes)])
                        for key in obj['status']:
                            missing.remove(int(key))
                        node2missing[i] = missing
                        for key in missing:
                            missing2node[key].add(i)
                    break
                except:
                    continue
        suspects = [suspects[i] + curr[i] for i in range(numNodes)]
        if isFirst:
            isFirst = False
            continue

        c = 0
        # write relationship between suspect count and inconsistency value
        with open(suspectFn, 'a') as f:
            for i in range(numNodes):
                c = max(c, len(missing2node[i]))
                if curr[i] == 0 or len(missing2node[i]) == 0:
                    continue
                f.write(f'{len(missing2node[i])} {curr[i]/numNodes}\n')
        if c == 0:
            continue
        # sample nodes to estimate inconsistency value c multiple times
        for _ in range(numSample):
            targets = np.random.choice(range(numNodes), sampleSize, replace=False)
            tops = {}
            for node in targets:
                for miss in node2missing[node]:
                    tops[miss] = tops.get(miss, 0) + 1
            arr = [tops[k] for k in tops]
            m = 0 if len(arr) == 0 else max(arr)
            estimate = m * numNodes / sampleSize
            # if estimate == -c:
            #     continue
            with open(estimateFn, 'a') as f:
                f.write(f'{c} {estimate}\n')


def plot_estimate(dropRate):
    fig, ax = plt.subplots(figsize=(10, 5))

    x = np.arange(len(numNodes))
    offset = -0.2
    colors = ['red', 'green', 'blue']
    for i in range(len(sampleSizes)):
        sampleSize = sampleSizes[i]
        means = []
        stds = []
        for numNode in numNodes:
            fn = f'result/{numNode}/{dropRate}/estimate_{sampleSize}_{numSample}.txt'
            with open(fn, 'r') as f:
                lines = f.readlines()
                curr = np.asarray([float(v.split()[1]) - float(v.split()[0]) for v in lines])
                means.append(curr.mean())
                stds.append(curr.std())
        ax.errorbar(x + offset, means, stds, color=colors[i], fmt='o', capsize=10, markersize=10)
        offset += 0.2

    ax.set_xticks(x)
    ax.set_xticklabels(numNodes)
    ax.set_xlabel('num nodes')
    ax.set_ylabel('average of estimate - actual c')

    handle1, = plt.plot([1, 1], '-', color=colors[0], marker='o')
    handle2, = plt.plot([1, 1], '-', color=colors[1], marker='o')
    handle3, = plt.plot([1, 1], '-', color=colors[2], marker='o')
    ax.legend([handle1, handle2, handle3], ['size=10', 'size=15', 'size=20'])
    handle1.set_visible(False)
    handle2.set_visible(False)
    handle3.set_visible(False)

    plt.grid()
    plt.savefig(f'estimate_{dropRate}.png')


def plot_suspect(dropRate):
    xs = []
    ys = []
    for sampleSize in sampleSizes:
        for numNode in numNodes:
            fn = f'result/{numNode}/{dropRate}/suspect_{sampleSize}_{numSample}.txt'
            with open(fn, 'r') as f:
                lines = f.readlines()
                xs += [float(v.split()[0]) for v in lines]
                ys += [float(v.split()[1]) for v in lines]
    fig, ax = plt.subplots(figsize=(10, 5))
    ax.scatter(xs, ys)
    ax.set_xlabel('actual c')
    ax.set_ylabel('average suspect at each node')

    plt.grid()
    plt.savefig(f'suspect_{dropRate}.png')


if __name__ == '__main__':
    # estimate_func(int(sys.argv[1]), float(sys.argv[2]), 10, int(sys.argv[3]))
    plot_estimate(0.1)
    plot_estimate(0.2)
    plot_suspect(0.1)
    plot_suspect(0.2)
