import matplotlib.pyplot as plt
import json
import argparse
import numpy as np
import os
import plot_setting as ps

powerks = ['0.0', '0.25', '0.5', '0.75', '1.0', '1.25', '1.5', '1.75', '2.0', '2.25', '2.5', '2.75', '3.0', '3.25', '3.5', '3.75', '4.0', '4.25', '4.5', '4.75', '5.0']
strategies = ['naive', 'active', 'passive', 'act_pas']
num_server = 49

def get_data(folder):
    avgHop = []
    print(folder)
    for idx in range(num_server):
        fn = folder + str(idx) + '.json'
        if not os.path.exists(fn):
            print("file " + fn + " does not exists")
            continue
        f = open(fn)
        stat = json.load(f)
        for run in stat['runs']:
            IndMsg = int(run['ttlIndMsgNum'])
            Msg = int(run['ttlMsgNum'])
            avgHop.append(IndMsg / Msg)
    avgHop = np.asarray(avgHop)
    return avgHop.mean(), avgHop.std()

if __name__ == '__main__':
    path = 'all_random_49_hop_distance'
    dest = 'all_random_49_hop_distance/avgHop.txt'
    if not os.path.exists(dest):
        mean = []
        std = []
        for powerk in powerks:
            for strategy in strategies:
                folder = path + '/' + powerk + '/' + strategy + '/'
                if not os.path.isdir(folder):
                    print(folder + ' does not exists')
                    assert False
                m, s = get_data(folder)
                mean.append(m)
                std.append(s)
    else:
        arr = np.loadtxt(dest)
        mean, std = arr[0], arr[1]

    np.savetxt(dest, np.asarray([mean, std]))
    mean = np.asarray(mean).reshape(21, 4).T
    std = np.asarray(std).reshape(21, 4).T

    fig, ax = plt.subplots()
    ax.grid()
    ax.set_axisbelow(True)
    for i, strategy in enumerate(strategies):
        ax.errorbar(np.arange(21)/4, mean[i], # yerr=std[i], 
            label=ps.get_line_legend(strategy), color=ps.get_edge_color(strategy), 
            linestyle=ps.get_line_pattern(strategy), marker=ps.get_line_marker(strategy))

    ax.set_ylabel('Average hop number')
    ax.set_xlabel(r'Exponent $m$')
    # ax.set_xticks(np.arange(21))
    # ax.set_xticklabels(powerks, rotation=45)
    ax.set_ylim(bottom=1, top=3.5)
    ax.set_yticks(np.arange(1, 3.6, 0.5))
    ax.legend(loc='upper right', ncol=1, bbox_to_anchor=(1, 1), fontsize=10)
    fig.set_size_inches(5, 2.5)
    fig.tight_layout(rect=[-0.03, -0.09, 1.01, 1.05])
    plt.savefig(path + '/avgHop.pdf')
