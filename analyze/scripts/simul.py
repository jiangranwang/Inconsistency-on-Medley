import matplotlib.pyplot as plt
import json
import argparse
import numpy as np
import os
import plot_setting as ps

failures = np.array([1, 4, 8, 12, 16, 20, 24])
strategies = ['naive', 'active', 'passive', 'act_pas']
parser = argparse.ArgumentParser()
parser.add_argument('--path', dest='path', type=str, required=False, default='simul_random_49/')
parser.add_argument('--dest', dest='dest', type=str, required=False, default='simul_random_49/test.txt')
normalize = 100
timeout = 16000

def get_data(fn):
    fstDetectionMean = []
    fstDetectionLow = []
    fstDetectionHigh = []
    print(fn)
    f = open(fn)
    stat = json.load(f)
    for run in stat['runs']:
        failDetectionTime = run['failDetectionTime']
        curr = []
        for failNode in failDetectionTime:
            arr = []
            for entry in failDetectionTime[failNode]:
                if int(entry['time']) < timeout:
                    continue
                arr.append(int(entry['time']) / normalize)
            if len(arr) == 0:
                continue
            curr.append(min(arr))
        if len(curr) == 0:
            continue
        curr = np.asarray(curr)
        fstDetectionMean.append(curr.mean())
        fstDetectionLow.append(curr.min())
        fstDetectionHigh.append(curr.max())
    fstDetectionMean = np.asarray(fstDetectionMean)
    fstDetectionLow = np.asarray(fstDetectionLow)
    fstDetectionHigh = np.asarray(fstDetectionHigh)
    return fstDetectionMean.mean(), fstDetectionLow.mean(), fstDetectionHigh.mean()

if __name__ == '__main__':
    args = parser.parse_args()
    if args.path[-1] == '/':
        args.path = args.path[:-1]
    if not os.path.exists(args.dest):
        lss = []
        mss = []
        hss = []
        for path in ['/0.0/naive/', '/1.5/naive/', '/3.0/naive/', '/3.0/act_pas/']:
            for num_failure in failures:
                fn = args.path + path + str(num_failure) + '.json'
                if not os.path.exists(fn):
                    print(fn + ' does not exists')
                    assert False
                ms, ls, hs = get_data(fn)
                lss.append(ls)
                mss.append(ms)
                hss.append(hs)
    else:
        arr = np.loadtxt(args.dest)
        lss, mss, hss = arr[0], arr[1], arr[2]

    np.savetxt(args.dest, np.asarray([lss, mss, hss]))
    lss = np.asarray(lss).reshape(4, 7)
    mss = np.asarray(mss).reshape(4, 7)
    hss = np.asarray(hss).reshape(4, 7)
    lss = mss - lss
    hss = hss - mss

    fig, ax = plt.subplots()
    ax.grid()
    ax.bar(np.arange(7)-0.2, mss[0], 0.2, yerr=[lss[0], hss[0]], label='base SWIM(m=0)', 
        color=ps.get_bar_color('naive'), hatch=ps.get_pattern('naive'),
        edgecolor=ps.get_edge_color('naive'),
        error_kw=dict(ecolor='k', lw=1.5, capsize=2.5, capthick=1))
    # ax.bar(np.arange(7)-0.1, mss[1], 0.2, yerr=[lss[1], hss[1]], label='Medley (m=1.5)', 
    #     color=ps.get_bar_color('active'), hatch=ps.get_pattern('active'),
    #     edgecolor=ps.get_edge_color('active'),
    #     error_kw=dict(ecolor='k', lw=1.5, capsize=2.5, capthick=1))
    ax.bar(np.arange(7), mss[2], 0.2, yerr=[lss[2], hss[2]], label='Medley (m=3)', 
        color=ps.get_bar_color('active'), hatch=ps.get_pattern('active'),
        edgecolor=ps.get_edge_color('active'),
        error_kw=dict(ecolor='k', lw=1.5, capsize=2.5, capthick=1))
    ax.bar(np.arange(7)+0.2, mss[3], 0.2, yerr=[lss[3], hss[3]], label='act & pas (m=3)', 
        color=ps.get_bar_color('passive'), hatch=ps.get_pattern('passive'),
        edgecolor=ps.get_edge_color('passive'),
        error_kw=dict(ecolor='k', lw=1.5, capsize=2.5, capthick=1))

    ax.set_ylabel('First detection time\n(time unit)')
    ax.set_xlabel('Number of failures')
    ax.set_ylim(top=600)
    # ax.set_title('Average First Detection Time under Simultaneous Failure')
    ax.set_xticks(np.arange(7))
    ax.set_xticklabels(failures)
    ax.legend(loc='upper left', ncol=1, bbox_to_anchor=(0.0, 1.0), fontsize=10)
    fig.set_size_inches(6, 2)
    fig.tight_layout(rect=[-0.02, -0.07, 1, 1.02])
    plt.savefig('simul.pdf')
