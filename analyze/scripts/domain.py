import argparse
import json
import matplotlib.pyplot as plt
import numpy as np
import os
import plot_setting as ps
from scipy.optimize import curve_fit
import warnings

warnings.filterwarnings('ignore')


powerks = ['0.0', '1.0', '2.0', '3.0', '4.0', '5.0']
strategies = ['naive', 'active', 'passive', 'act_pas']
parser = argparse.ArgumentParser()
parser.add_argument('--path', dest='path', type=str, required=False, default='domain_cluster_49/')
parser.add_argument('--dest', dest='dest', type=str, required=False, default='domain_cluster_49/test.txt')
normalize = 100
timeout = 16000

def get_data(folder):
    fstDetectionMean = []
    fstDetectionLow = []
    fstDetectionHigh = []
    print(folder)
    for cluster in range(1, 6):
        fn = folder + str(cluster) + '.json'
        if not os.path.exists(fn):
            print("file " + fn + " does not exists")
            continue
        f = open(fn)
        stat = json.load(f)
        for run in stat['runs']:
            curr = []
            failDetectionTime = run['failDetectionTime']
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


def func(x,a,b,c):
   return a * np.exp(-b*x) - c


def dataCurveFit(x, y, fname, post_fix='.png', skip_exp=False):
    plt.plot(x, y, 'o', label='Original data')

    # Fit linewar
    m, b = np.polyfit(x, y, 1)
    plt.plot(x, m*x + b, label='Linear fit')

    if not skip_exp:    
        popt, pcov = curve_fit(func, x, y)
        y_exp = func(x, *popt)
        plt.plot(x, y_exp, label='Exponential fit')

    plt.savefig(fname + post_fix)
    plt.clf()

def conductCurveFit(mss, args):
    dataCurveFit(np.arange(6), mss[0], args.path + '/medley_curve')
    dataCurveFit(np.arange(5), mss[0][:-1], args.path + '/medley_curve_to4')
    dataCurveFit(np.arange(6), mss[1], args.path + '/active_curve', skip_exp=True)
    dataCurveFit(np.arange(5), mss[0][:-1], args.path + '/active_curve_to4')
    dataCurveFit(np.arange(6), mss[2], args.path + '/passive_curve', skip_exp=True)
    dataCurveFit(np.arange(5), mss[2][:-1], args.path + '/passive_curve_to4')
    dataCurveFit(np.arange(6), mss[3], args.path + '/act_pas_curve', skip_exp=True)
    dataCurveFit(np.arange(5), mss[3][:-1], args.path + '/act_pas_curve_to4')
    

if __name__ == '__main__':
    args = parser.parse_args()
    if args.path[-1] == '/':
        args.path = args.path[:-1]
    if not os.path.exists(args.dest):
        lss = []
        mss = []
        hss = []
        for powerk in powerks:
            for strategy in strategies:
                folder = args.path + '/' + powerk + '/' + strategy + '/'
                if not os.path.isdir(folder):
                    print(folder + ' does not exists')
                    assert False
                ms, ls, hs = get_data(folder)
                lss.append(ls)
                mss.append(ms)
                hss.append(hs)
    else:
        arr = np.loadtxt(args.dest)
        lss, mss, hss = arr[0], arr[1], arr[2]

    np.savetxt(args.dest, np.asarray([lss, mss, hss]))
    lss = np.asarray(lss).reshape(6, 4).T
    mss = np.asarray(mss).reshape(6, 4).T
    hss = np.asarray(hss).reshape(6, 4).T
    lss = mss - lss
    hss = hss - mss

    # conductCurveFit(mss, args)
    
    fig, ax = plt.subplots()
    ax.grid()
    ax.bar(np.arange(6)-0.3, mss[0], 0.2, yerr=[lss[0], hss[0]], label='Medley', 
        color=ps.get_bar_color('naive'), hatch=ps.get_pattern('naive'),
        edgecolor=ps.get_edge_color('naive'),
        error_kw=dict(ecolor='k', lw=1.5, capsize=2.5, capthick=1))
    ax.bar(np.arange(6)-0.1, mss[1], 0.2, yerr=[lss[1], hss[1]], label='active', 
        color=ps.get_bar_color('active'), hatch=ps.get_pattern('active'),
        edgecolor=ps.get_edge_color('active'),
        error_kw=dict(ecolor='k', lw=1.5, capsize=2.5, capthick=1))
    ax.bar(np.arange(6)+0.1, mss[2], 0.2, yerr=[lss[2], hss[2]], label='passive', 
        color=ps.get_bar_color('passive'), hatch=ps.get_pattern('passive'),
        edgecolor=ps.get_edge_color('passive'),
        error_kw=dict(ecolor='k', lw=1.5, capsize=2.5, capthick=1))
    ax.bar(np.arange(6)+0.3, mss[3], 0.2, yerr=[lss[3], hss[3]], label='act & pas', 
        color=ps.get_bar_color('act_pas'), hatch=ps.get_pattern('act_pas'),
        edgecolor=ps.get_edge_color('act_pas'),
        error_kw=dict(ecolor='k', lw=1.5, capsize=2.5, capthick=1))

    ax.set_ylabel('First detection time\n(time unit)')
    ax.set_xlabel(r'Exponent $m$')
    ax.set_ylim(top=1000)
    # ax.set_title('Detection Time under Domain Failure')
    ax.set_xticks(np.arange(6))
    ax.set_xticklabels(powerks)
    ax.legend(loc='upper left', ncol=1, bbox_to_anchor=(0.0, 1.02), fontsize=10)
    fig.set_size_inches(6, 2)
    fig.tight_layout(rect=[-0.02, -0.1, 1.01, 1.04])
    plt.savefig('domain.pdf')
