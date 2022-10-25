import argparse
import json
import math
import matplotlib.pyplot as plt
import numpy as np
import os
import pandas as pd
import subprocess
import plot_setting as ps

parser = argparse.ArgumentParser()
parser.add_argument('--path', dest='path', type=str, required=False, default='all_random_49_hop_distance/')
parser.add_argument('--interval', dest='interval', type=float, required=False, default=0.5)
# parser.add_argument('--dest', dest='dest', type=str, required=False, default='all_random_49/test.txt')
args = parser.parse_args()
num_server = int(args.path.strip('/').split('_')[2])
# num_server = 49
# num_run = 25

if args.path[-1] == '/':
    args.path = args.path[:-1]


class MedleyStats:
    powerk = None
    strategy = None
    numMsg = []
    numH2hMsg = []
    numPassive = []
    numPingToFailedNode = []
    firstDetectDist = []
    numFalsePositive = []
    numFalseNegative = []
    timeFstDetect = []
    timeLstDetect = []
    timeAvgDetect = []
    types = {}



def print_result(msg):
    print(msg)
    with open(args.dest, 'a') as f:
        f.write(msg)
        f.write('\n')


def info(folder, k, strategy):
    
    all_stats = []
    
    for i in range(num_server):
        fn = folder + str(i) + '.json'
        if not os.path.exists(fn):
            print("file " + fn + " does not exists")
            continue
        
        f = open(fn)
        stat = json.load(f)
        for d in stat['runs']:
            stats = MedleyStats()
            stats.powerk = k
            stats.strategy = strategy
            stats.numMsg = (int(d['ttlMsgNum']))
            stats.numH2hMsg = (int(d['ttlIndMsgNum']))
            stats.numPassive = (int(d['ttlPassive']))
            # ttlPingToFailedNode += int(d['ttlPingToFailedNode'])
            stats.firstDetectDist.extend([float(i) for i in d['firstDetectDistance']])
            stats.numFalsePositive = (int(d['falsePositives']))
            stats.numFalseNegative = (int(d['falseNegatives']))
            stats.timeFstDetect = (int(d['avgFirstTime']))
            stats.timeLstDetect = (int(d['avgLastTime']))
            stats.timeAvgDetect = (int(d['avgTime']))
            # for k in types:
            #     types[k] += (int(d['msgType'][k]))
            all_stats.append(stats)

    return all_stats


def getOneTypeStatistic(all_stats, num_row, num_col, target):
    results = []
    for i in range(num_row):
        results.append([ eval('all_stats[i][j].' + target) for j in range(num_col)])
    return results


def aggregateData(args, to_file=False, fname='aggregated_data.csv'):
    all_stats = []
    arr_powerk = np.arange(0.0, 5.1, args.interval)
    arr_strategy = ['naive', 'active', 'passive', 'act_pas']
    for k in arr_powerk: 
        for sub in arr_strategy:
            print("Evaluating k = " + str(k) + ' strategy ' + sub)
            folder = args.path + '/' + str(k) + '/' + sub + '/'
            if not os.path.isdir(folder):
                print('Data not exists for ' + folder)
                continue
            
            single_stats = info(folder, k, sub)
            all_stats.extend(single_stats)
    
    df = pd.DataFrame([stt.__dict__ for stt in all_stats])
    print(df)
    if to_file:
        df.to_csv(args.path + '/' + fname, index=False)


def groupAndDraw(df, x_col, y_col, line_col, sqrt_std=False):
    result = df.groupby(['powerk', 'strategy'], as_index=False).agg(
        mean=(y_col, 'mean'),
        std=(y_col, 'std'),
        min=(y_col, 'min'),
        max=(y_col, 'max'))

    fig, ax = plt.subplots()
    if sqrt_std:
        result['sqrt_std'] = np.sqrt(result['std'])
        keys = ['naive', 'active', 'passive', 'act_pas']
        grouped = result.groupby(line_col)
        for key in keys:
            grp = grouped.get_group(key)
            ax = grp.plot(kind='line', x=x_col, y='mean', ax=ax, 
                          label=ps.get_line_legend(key),
                          # yerr='sqrt_std', capsize=2,
                          color=ps.get_edge_color(key), linestyle=ps.get_line_pattern(key),
                          marker=ps.get_line_marker(key))
    else:
        fig, ax = plt.subplots()
        for key, grp in result.groupby(line_col):
            ax = grp.plot(kind='line', x=x_col, y='mean', ax=ax, label=key,
                          yerr='std', capsize=2)

    plt.tight_layout()
    return fig, ax


def savePlotAndClear(fname, postfix='.pdf'):
    # plt.tight_layout()
    plt.savefig(fname + postfix, dpi=300)
    plt.clf()


def drawDetectionTime(df, path):
    # First detection time
    df_target = df[['timeFstDetect', 'powerk', 'strategy']]
    groupAndDraw(df_target, 'powerk', 'timeFstDetect', 'strategy')
    plt.ylim(15000, 30000)
    savePlotAndClear(path + '/fst_detection_time')
    groupAndDraw(df_target, 'powerk', 'timeFstDetect', 'strategy', sqrt_std=True)
    plt.ylim(20000, 25000)
    savePlotAndClear(path + '/fst_detection_time_sqrt_std')

    # Lst detection time
    df_target = df[['timeLstDetect', 'powerk', 'strategy']]
    groupAndDraw(df_target, 'powerk', 'timeLstDetect', 'strategy')
    # plt.ylim(15000, 30000)
    savePlotAndClear(path + '/lst_detection_time')
    groupAndDraw(df_target, 'powerk', 'timeLstDetect', 'strategy', sqrt_std=True)
    plt.ylim(20000, 48000)
    savePlotAndClear(path + '/lst_detection_time_sqrt_std')


    # Lst detection time
    df_target = df[['timeAvgDetect', 'powerk', 'strategy']]
    groupAndDraw(df_target, 'powerk', 'timeAvgDetect', 'strategy')
    # plt.ylim(15000, 30000)
    savePlotAndClear(path + '/avg_detection_time')
    fig, ax = groupAndDraw(df_target, 'powerk', 'timeAvgDetect', 'strategy', sqrt_std=True)
    fig.set_size_inches(6, 4)
    plt.ylim(20000, 35000)
    savePlotAndClear(path + '/avg_detection_time_sqrt_std')
    

def drawCrossProduct(df, path, time_col='timeAvgDetect', msg_col='numH2hMsg'):
    df = df[[msg_col, time_col, 'powerk', 'strategy']]
    df['value'] = df[time_col] * df[msg_col]
    df['vsqrt'] = np.sqrt((df[time_col] * df[msg_col]))
    df = df[['vsqrt', 'powerk', 'strategy']]

    fig, ax = groupAndDraw(df, 'powerk', 'vsqrt', 'strategy', sqrt_std=True)
    plt.ylabel(r'sqrt( Detection Time $\times$' +'\nCommunication Cost )')
    plt.xlabel(r'Exponent $m$')
    plt.ylim(25000, 50000)
    plt.grid()
    plt.legend()
    ax.set_axisbelow(True)
    fig.set_size_inches(6, 2.5)
    fig.tight_layout(rect=[-0.02, -0.07, 1.0, 1.05])
    savePlotAndClear(path + '/tradeoff_' + msg_col + '_' + time_col + '_sqrt_std')


def drawCrossValue(df, path, time_col='timeFstDetect', msg_col='numH2hMsg'):
    df = df[[msg_col, time_col, 'powerk', 'strategy']]
    df['value'] = df[time_col] * df[msg_col]
    df['vsqrt'] = np.sqrt((df[time_col] * df[msg_col]))
    df = df[['vsqrt', 'powerk', 'strategy']]

    # fig, ax = groupAndDraw(df, 'powerk', 'vsqrt', 'strategy')
    # plt.ylabel('sqrt(' + time_col + ' * ' + msg_col + ')')
    # plt.xlabel('power k')
    # savePlotAndClear(path + '/tradeoff_' + msg_col + '_' + time_col + '')

    fig, ax = groupAndDraw(df, 'powerk', 'vsqrt', 'strategy', sqrt_std=True)
    plt.ylabel('sqrt(' + time_col + ' * ' + msg_col + '), yerr=sqrt(std)')
    plt.xlabel('power k')
    fig.set_size_inches(6, 3)
    savePlotAndClear(path + '/tradeoff_' + msg_col + '_' + time_col + '_sqrt_std')

    
    # print(result.columns.to_list())


if __name__ == '__main__':
    fname_data = 'raw_aggregated_data.csv'
    if not os.path.exists(args.path + '/' + fname_data):
        aggregateData(args, True, fname_data)

    df = pd.read_csv(args.path + '/' + fname_data)
    # # print(df.columns.to_list())
    # drawDetectionTime(df, args.path)
    drawCrossProduct(df, args.path)
    # drawCrossValue(df, args.path, time_col='timeAvgDetect')
    # drawCrossValue(df, args.path, msg_col='numMsg')
