import matplotlib.pyplot as plt
import json
import argparse
import numpy as np
import os
import plot_setting as ps

# powerks = ['0.0', '0.25', '0.5', '0.75', '1.0', '1.25', '1.5', '1.75', '2.0', '2.25', '2.5', '2.75', '3.0', '3.25', '3.5', '3.75', '4.0', '4.25', '4.5', '4.75', '5.0']
powerk = '3.0'
strategies = ['naive', 'active', 'passive', 'act_pas']
settings = ["static", "0.1_2", "0.1_4", "0.1_8", "0.1_16", "0.2_2", "0.2_4", "0.2_8", "0.2_16", "0.4_2", "0.4_4", "0.4_8", "0.4_16"]
normalize = 100
timeout = 16000
num_server = 49

def get_data(folder):
    fstDetectionTime = []
    lstDetectionTime = []
    print(folder)
    for idx in range(num_server):
        fn = folder + str(idx) + '.json'
        if not os.path.exists(fn):
            print("file " + fn + " does not exists")
            continue
        f = open(fn)
        stat = json.load(f)
        for run in stat['runs']:
            failDetectionTime = run['failDetectionTime']
            for failNode in failDetectionTime:
                arr = []
                for entry in failDetectionTime[failNode]:
                    if int(entry['time']) < timeout:
                        continue
                    arr.append(int(entry['time']) / normalize)
                if len(arr) == 0:
                    continue
                arr.sort()
                fstDetectionTime.append(arr[0])
                lstDetectionTime.append(arr[-1])
    fdt = np.asarray(fstDetectionTime) - 180
    ldt = np.asarray(lstDetectionTime) - 180
    # num_95p = floor(len(fdt) * 0.95)
    # fdt.sort()
    # ldt.sort()
    # return fdt[num_95p], np.sqrt(fdt.std()), ldt[num_95p], np.sqrt(ldt.std())
    return fdt.mean(), np.sqrt(fdt.std()), ldt.mean(), np.sqrt(ldt.std())

if __name__ == '__main__':
    path = 'mobile_random_49/'
    dest = 'mobile_random_49/mobile.txt'
    # if not os.path.exists(dest):
    fms = []
    fss = []
    lms = []
    lss = []
    for strategy in strategies:
        folder = 'mobile_random_49' + '/' + powerk + '/' + strategy + '/0.0_0/'
        if not os.path.isdir(folder):
            print(folder + ' does not exists')
            assert False
        fm, fs, lm, ls = get_data(folder)
        fms.append(fm)
        fss.append(fs)
        lms.append(lm)
        lss.append(ls)
    for setting in settings[1:]:
        for strategy in strategies:
            folder = path + '/' + powerk + '/' + strategy + '/' + setting + '/'
            if not os.path.isdir(folder):
                print(folder + ' does not exists')
                assert False
            fm, fs, lm, ls = get_data(folder)
            fms.append(fm)
            fss.append(fs)
            lms.append(lm)
            lss.append(ls)

    # else:
    #     arr = np.loadtxt(dest)
    #     fms, fss, lms, lss = arr[0], arr[1], arr[2], arr[3]

    np.savetxt(dest, np.asarray([fms, fss, lms, lss]))
    fms = np.asarray(fms).reshape(len(settings), 4).T
    fss = np.asarray(fss).reshape(len(settings), 4).T
    lms = np.asarray(lms).reshape(len(settings), 4).T
    lss = np.asarray(lss).reshape(len(settings), 4).T

    # print('naive, fst', fms[0])
    # print('naive, lst', lms[0])
    # print('naive, dst', lms[0] - fms[0])
    # print('activ, fst', fms[1])
    # print('act_p, dst', lms[3] - fms[3])

    # ind_k = 20
    # print('fst:  ', [fms[0][ind_k] - fms[stt][ind_k] for stt in range(1, 4)])
    # print('fst%: ', [(fms[0][ind_k] - fms[stt][ind_k]) / (fms[0][ind_k] - 180) for stt in range(1, 4)])
    # print('dsm:  ', [lms[0][ind_k] - fms[0][ind_k] - lms[stt][ind_k] + fms[stt][ind_k] for stt in range(1, 4)])
    # print('dsm%: ', [(lms[0][ind_k] - fms[0][ind_k] - lms[stt][ind_k] + fms[stt][ind_k]) / (lms[0][ind_k] - fms[0][ind_k]) for stt in range(1, 4)])
    # exit()


    fig, ax = plt.subplots()
    ax.grid()
    ax.set_axisbelow(True)
    ax.bar(np.arange(len(settings))-0.3, fms[0], 0.18, yerr=fss[0], label='Medley, dfdt', 
        color=ps.get_bar_color('naive'), hatch=ps.get_pattern('naive'),
        edgecolor=ps.get_edge_color('naive'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(settings))-0.3, lms[0]-fms[0], 0.18, yerr=lss[0], bottom=fms[0], label='Medley, dsm',
        color=ps.get_bar_color('naive'), hatch=ps.get_pattern('naive', True),
        edgecolor=ps.get_edge_color('naive'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(settings))-0.1, fms[1], 0.18, yerr=fss[1], label='active, dfdt', 
        color=ps.get_bar_color('active'), hatch=ps.get_pattern('active'),
        edgecolor=ps.get_edge_color('active'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(settings))-0.1, lms[1]-fms[1], 0.18, yerr=lss[1], bottom=fms[1], label='active, dsm',
        color=ps.get_bar_color('active'), hatch=ps.get_pattern('active', True),
        edgecolor=ps.get_edge_color('active'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(settings))+0.1, fms[2], 0.18, yerr=fss[2], label='passive, dfdt', 
        color=ps.get_bar_color('passive'), hatch=ps.get_pattern('passive'),
        edgecolor=ps.get_edge_color('passive'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(settings))+0.1, lms[2]-fms[2], 0.18, yerr=lss[2], bottom=fms[2], label='passive, dsm',
        color=ps.get_bar_color('passive'), hatch=ps.get_pattern('passive', True),
        edgecolor=ps.get_edge_color('passive'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(settings))+0.3, fms[3], 0.18, yerr=fss[3], label='act & pas, dfdt', 
        color=ps.get_bar_color('act_pas'), hatch=ps.get_pattern('act_pas'),
        edgecolor=ps.get_edge_color('act_pas'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))
    ax.bar(np.arange(len(settings))+0.3, lms[3]-fms[3], 0.18, yerr=lss[3], bottom=fms[3], label='act & pas, dsm',
        color=ps.get_bar_color('act_pas'), hatch=ps.get_pattern('act_pas', True),
        edgecolor=ps.get_edge_color('act_pas'),
        error_kw=dict(ecolor='k', lw=1, capsize=2, capthick=1))

    ax.set_ylabel('Failure detection time\n(time unit)')
    ax.set_xlabel(r'Exponent $m$')
    # ax.set_title('First Detection Time of Failure')
    ax.set_xticks(np.arange(len(settings)))
    ax.set_xticklabels(settings, rotation=45)
    ax.set_ylim(0, 500)
    ax.set_yticks([0, 100, 200, 300])
    ax.legend(loc='upper left', ncol=2, bbox_to_anchor=(-0.005, 1.03), fontsize=10)
    fig.set_size_inches(10, 2.2)
    fig.tight_layout(rect=[-0.01, -0.10, 1.01, 1.05])
    plt.savefig(path + 'mobile.pdf')
