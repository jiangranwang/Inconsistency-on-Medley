import matplotlib.pyplot as plt
import json
import argparse
import numpy as np
import os
import plot_setting as ps

strategies = ['naive', 'active', 'passive', 'act_pas']
num_server = 49
num_run = 25
max_hop = 10

def bfs(graph, i):
    visited = [False for _ in range(num_server)]
    border = [i]
    visited[i] = True
    dist = 1
    while border:
        new_border = []
        for b in border:
            for n in range(num_server):
                if graph[b][n] != 1 or visited[n]:
                    continue
                graph[i][n] = dist
                new_border.append(n)
                visited[n] = True
        border = new_border
        dist += 1

def get_hop_info(fn):
    f = open(fn)
    cs = np.loadtxt(fn, delimiter=',')
    graph = [[0 for _ in range(num_server)] for _ in range(num_server)]
    for i in range(num_server):
        for j in range(i+1, num_server):
            dist = np.linalg.norm(cs[i] - cs[j])
            if dist <= 4:
                graph[i][j] = 1
                graph[j][i] = 1
    for i in range(num_server):
        bfs(graph, i)
    return graph

def get_data(folder):
    hops = np.zeros(max_hop+1)
    infos = []
    print(folder)
    for i in range(num_run):
        infos.append(get_hop_info(folder + 'topo/random_' + str(i+1) + '.txt'))
    for idx in range(num_server):
        fn = folder + str(idx) + '.json'
        if not os.path.exists(fn):
            print("file " + fn + " does not exists")
            assert False
        f = open(fn)
        stat = json.load(f)
        for i, run in enumerate(stat['runs']):
            for key in run['endEndMsg']:
                fr = int(key.split('|')[0])
                for key2 in run['endEndMsg'][key]:
                    if key2 == "total":
                        continue
                    to = int(key2.split('|')[0])
                    if fr == to:
                        continue
                    hop = infos[i][fr][to]
                    hops[hop] += int(run['endEndMsg'][key][key2])
    return hops

def getCDF(count):
    pdf = count / sum(count)
    return np.cumsum(pdf) * 100

if __name__ == '__main__':
    path = 'all_random_49'
    dest = 'all_random_49/cdfHop.txt'
    if not os.path.exists(dest):
        hops = []
        hops.append(get_data(path + '/0.0/naive/'))
        for strategy in strategies:
            folder = path + '/3.0/' + strategy + '/'
            if not os.path.isdir(folder):
                print(folder + ' does not exists')
                assert False
            hops.append(get_data(folder))
        hops.append(get_data(path + '/5.0/naive/'))
        hops = np.asarray(hops)
        np.savetxt(dest, hops)
    else:
        hops = np.loadtxt(dest)

    fig, ax = plt.subplots()
    ax.grid()
    ax.set_axisbelow(True)
    cdf = getCDF(hops[0])
    ax.plot(np.arange(max_hop+1), cdf, label='base SWIM (m=0)')
    for i, strategy in enumerate(strategies):
        cdf = getCDF(hops[i + 1])
        ax.plot(np.arange(max_hop+1), cdf, label=ps.get_line_legend(strategy, True), 
            color=ps.get_edge_color(strategy), linestyle=ps.get_line_pattern(strategy))

    # cdf = getCDF(hops[-1])
    # ax.plot(np.arange(max_hop+1), cdf, label='Medley (m=5)')
    
    ax.set_ylabel('CDF (%)')
    ax.set_xlabel('Hop number')
    ax.set_ylim(bottom=0, top=100)
    ax.set_xlim(left=0, right=6)
    ax.legend(loc='lower right', ncol=1, bbox_to_anchor=(1, 0), fontsize=10)
    fig.set_size_inches(4, 2.3)
    fig.tight_layout(rect=[-0.02,-0.07,1.0,1.02])
    plt.savefig(path + '/cdfHop.pdf')
