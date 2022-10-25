import matplotlib.pyplot as plt
import json
import argparse
import numpy as np
import os
import plot_setting as ps

powerk = '3.0'
strategies = ['naive', 'active', 'passive', 'act_pas']
normalize = 100
timeout = 16000
max_detection = 500000 - 100000
num_servers = [32, 64, 128, 256, 512, 1024, 2048]
num_run = 2000

def get_data(folder, num_server):
    fstDetectionTime = []
    lstDetectionTime = []
    fns = []
    # print(folder)
    for idx in range(num_run):
        fn = folder + str(idx) + '.json'
        if not os.path.exists(fn):
            # print("file " + fn + " does not exists")
            continue
        f = open(fn)
        stat = json.load(f)
        for run in stat['runs']:
            failDetectionTime = run['failDetectionTime']
            fn = int(run['falseNegatives'])
            fns.append(fn)
            # if fn > 0:
            #     # print('skipping false negative runs')
            #     continue
            for failNode in failDetectionTime:    
                arr = []# [max_detection] * fn
                for entry in failDetectionTime[failNode]:
                    if int(entry['time']) < timeout:
                        continue
                    arr.append(int(entry['time']) / normalize)
                if len(arr) == 0:
                    continue
                arr.sort()
                # print(arr[-1])
                fstDetectionTime.append(arr[0]) # += arr[:10]
                # lstDetectionTime.append(arr[-1]) # += arr[-10:]
                lstDetectionTime.append(arr[min(int(num_server/2 - 1), len(arr)-1)]) # += arr[-10:]

    avgFalseNegative = sum(fns) / len(fns)
    print(folder + ", average false negatives: " + str(avgFalseNegative))

    fdt = np.asarray(fstDetectionTime) - 180
    ldt = np.asarray(lstDetectionTime) - 180
    # num_95p = floor(len(fdt) * 0.95)
    # fdt.sort()
    # ldt.sort()
    # return fdt[num_95p], np.sqrt(fdt.std()), ldt[num_95p], np.sqrt(ldt.std())
    return fdt.mean(), np.sqrt(fdt.std()), ldt.mean(), np.sqrt(ldt.std()), avgFalseNegative

if __name__ == '__main__':
    fms = []
    fss = []
    lms = []
    lss = []
    fns = []
    for num_server in num_servers:
        path = 'random_' + str(num_server)
        for strategy in strategies:
            folder = path + '/' + powerk + '/' + strategy + '/'
            if not os.path.isdir(folder):
                print(folder + ' does not exists')
                assert False
            fm, fs, lm, ls, fn = get_data(folder, num_server)
            fms.append(fm)
            fss.append(fs)
            lms.append(lm)
            lss.append(ls)
            fns.append(fn)

    # np.savetxt(dest, np.asarray([fms, fss, lms, lss]))
    fms = np.asarray(fms).reshape(len(num_servers), 4).T
    fss = np.asarray(fss).reshape(len(num_servers), 4).T
    lms = np.asarray(lms).reshape(len(num_servers), 4).T
    lss = np.asarray(lss).reshape(len(num_servers), 4).T
    fns = np.asarray(fns).reshape(len(num_servers), 4).T

    fig, ax = plt.subplots()
    ax.grid()
    ax.set_axisbelow(True)
    ax.bar(np.arange(len(num_servers))-0.3, fms[0], 0.18, yerr=fss[0], label='Medley, dfdt', 
        color=ps.get_bar_color('naive'), hatch=ps.get_pattern('naive'),
        edgecolor=ps.get_edge_color('naive'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(num_servers))-0.3, lms[0]-fms[0], 0.18, yerr=lss[0], bottom=fms[0], label='Medley, mdsm',
        color=ps.get_bar_color('naive'), hatch=ps.get_pattern('naive', True),
        edgecolor=ps.get_edge_color('naive'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(num_servers))-0.1, fms[1], 0.18, yerr=fss[1], label='active, dfdt', 
        color=ps.get_bar_color('active'), hatch=ps.get_pattern('active'),
        edgecolor=ps.get_edge_color('active'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(num_servers))-0.1, lms[1]-fms[1], 0.18, yerr=lss[1], bottom=fms[1], label='active, mdsm',
        color=ps.get_bar_color('active'), hatch=ps.get_pattern('active', True),
        edgecolor=ps.get_edge_color('active'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(num_servers))+0.1, fms[2], 0.18, yerr=fss[2], label='passive, dfdt', 
        color=ps.get_bar_color('passive'), hatch=ps.get_pattern('passive'),
        edgecolor=ps.get_edge_color('passive'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(num_servers))+0.1, lms[2]-fms[2], 0.18, yerr=lss[2], bottom=fms[2], label='passive, mdsm',
        color=ps.get_bar_color('passive'), hatch=ps.get_pattern('passive', True),
        edgecolor=ps.get_edge_color('passive'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(num_servers))+0.3, fms[3], 0.18, yerr=fss[3], label='act & pas, dfdt', 
        color=ps.get_bar_color('act_pas'), hatch=ps.get_pattern('act_pas'),
        edgecolor=ps.get_edge_color('act_pas'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(num_servers))+0.3, lms[3]-fms[3], 0.18, yerr=lss[3], bottom=fms[3], label='act & pas, mdsm',
        color=ps.get_bar_color('act_pas'), hatch=ps.get_pattern('act_pas', True),
        edgecolor=ps.get_edge_color('act_pas'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))

    ax.set_ylabel('Failure detection time\n(time unit)')
    ax.set_xlabel(r'Number of servers $m$')
    # ax.set_title('First Detection Time of Failure')
    ax.set_xticks(np.arange(len(num_servers)))
    ax.set_xticklabels(num_servers, rotation=45)
    ax.set_ylim(0, 300)
    # ax.set_yticks([0, 100, 200, 300])
    ax.legend(loc='upper left', ncol=2, bbox_to_anchor=(-0.005, 1.03), fontsize=10)


    fig.set_size_inches(6, 3)
    # fig.tight_layout(rect=[-0.01, -0.10, 1.01, 1.05])
    fig.tight_layout()
    plt.savefig('multi-median.pdf')
