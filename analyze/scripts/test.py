import numpy as np
import subprocess
import argparse
import os
import json
import matplotlib.pyplot as plt

parser = argparse.ArgumentParser()
parser.add_argument('--path', dest='path', type=str, required=True)
parser.add_argument('--dest', dest='dest', type=str, required=True)
args = parser.parse_args()
num_server = 49
num_run = 25

if args.path[-1] == '/':
    args.path = args.path[:-1]

def print_result(msg):
    print(msg)
    with open(args.dest, 'a') as f:
        f.write(msg)
        f.write('\n')


def info(folder):
    ttlMsg = 0
    ttlIndMsg = 0
    ttlPassive = 0
    ttlPingToFailedNode = 0
    firstDetectDist = []
    falsePositive = 0
    falseNegative = 0
    firstDetect = []
    lastDetect = []
    avgDetect = []
    types = {'PING': 0, 'IND_PING': 0, 'ACK': 0, 'IND_PING_ACK': 0, 'UNLUCKY': 0, 'REPORT_ALIVE': 0}

    count = 0
    for i in range(num_server):
        fn = folder + str(i) + '.json'
        if not os.path.exists(fn):
            print("file " + fn + " does not exists")
            continue
        count += 1
        f = open(fn)
        stat = json.load(f)
        for d in stat['runs']:
            ttlMsg += int(d['ttlMsgNum'])
            ttlIndMsg += int(d['ttlIndMsgNum'])
            ttlPassive += int(d['ttlPassive'])
            ttlPingToFailedNode += int(d['ttlPingToFailedNode'])
            firstDetectDist += [float(i) for i in d['firstDetectDistance']]
            falsePositive += int(d['falsePositives'])
            falseNegative += int(d['falseNegatives'])
            firstDetect.append(int(d['avgFirstTime']))
            lastDetect.append(int(d['avgLastTime']))
            avgDetect.append(int(d['avgTime']))
            for k in types:
                types[k] += int(d['msgType'][k])

    avgConst = num_run * count
    print_result("Average total message: " + str(ttlMsg / avgConst))
    print_result("Average hop to hop total message: " + str(ttlIndMsg / avgConst))
    print_result("Average passive number: " + str(ttlPassive / avgConst))
    print_result("Average first detection time: " + str(sum(firstDetect) / avgConst))
    print_result("Average last detection time: " + str(sum(lastDetect) / avgConst))
    print_result("Average overall detection time: " + str(sum(avgDetect) / avgConst))
    print_result("Average total ping to failed node: " + str(ttlPingToFailedNode / avgConst))
    print_result("Average distance to first detection: " + str(sum(firstDetectDist) / len(firstDetectDist)))
    print_result("Average number of false positives: " + str(falsePositive / avgConst))
    print_result("Average number of false negatives: " + str(falseNegative  / avgConst))

    keys = []
    nums = []
    for k in types:
        keys.append(k)
        nums.append(types[k] / avgConst)
    return keys, nums

if __name__ == '__main__':
    for sub in ['naive', 'active', 'passive', 'act_pas']:
        folder = args.path + '/' + sub + '/'
        if not os.path.isdir(folder):
            continue
        print_result(folder)
        keys, nums = info(folder)

        plt.figure(figsize=(10,7))
        plt.bar(keys, nums, width=0.5)
        plt.title(args.path)
        plt.xlabel('message type')
        plt.ylabel('message number')
        plt.savefig(args.path + '/' + sub + '.png')
        print_result('')