import numpy as np


location_fn = 'topo/grid10,25.txt'
dest_fn = 'topo/grid10,10_route.txt'
radius = 4
f = open(location_fn, 'r')
cs = f.readlines()
cs = [(float(c.split(',')[0]), float(c.split(',')[1])) for c in cs]

dist_map = {}
for c1 in cs:
    for c2 in cs:
        dist = np.sqrt((c1[0] - c2[0]) ** 2 + (c1[1] - c2[1]) ** 2)
        if c1 not in dist_map:
            dist_map[c1] = {}
        if c2 not in dist_map:
            dist_map[c2] = {}
        dist_map[c1][c2] = dist
        dist_map[c2][c1] = dist

hop_dist_map = {}
route_table = {}
hop_num_map = {}
for c in cs:
    hop_dist_map[c] = {}
    route_table[c] = {}
    hop_num_map[c] = {}

for c1 in cs:
    for c2 in cs:
        dist = dist_map[c1][c2]
        if dist > radius:
            hop_dist_map[c1][c2] = 1000000
            hop_dist_map[c2][c1] = 1000000
        else:
            hop_dist_map[c1][c2] = dist
            hop_dist_map[c2][c1] = dist
            route_table[c1][c2] = c2
            route_table[c2][c1] = c1

for c0 in cs:
    for c1 in cs:
        for c2 in cs:
            if hop_dist_map[c1][c0] + hop_dist_map[c0][c2] < hop_dist_map[c1][c2]:
                hop_dist_map[c1][c2] = hop_dist_map[c1][c0] + hop_dist_map[c0][c2]
                hop_dist_map[c2][c1] = hop_dist_map[c1][c0] + hop_dist_map[c0][c2]
                route_table[c1][c2] = c0
                route_table[c2][c1] = c0

for c1 in cs:
    for c2 in cs:
        if c1 not in route_table[c2]:
            assert False, 'network partition'

for c1 in cs:
    for c2 in cs:
        if c1[0] == c2[0] and c1[1] == c2[1]:
            hop_num_map[c1][c2] = 0
            continue
        next_hop = route_table[c1][c2]
        hop = 1
        while not (next_hop[0] == c2[0] and next_hop[1] == c2[1]):
            next_hop = route_table[next_hop][c2]
            hop += 1
        hop_num_map[c1][c2] = hop

c2i = {}
for i, c in enumerate(cs):
    c2i[c] = str(i)

f = open(dest_fn, 'w')
f.write('dist_map:\n')
for c1 in dist_map:
    for c2 in dist_map:
        f.write(c2i[c1] + ',' + c2i[c2] + ',' + str(dist_map[c1][c2]) + '\n')
f.write('hop_dist_map:\n')
for c1 in dist_map:
    for c2 in dist_map:
        f.write(c2i[c1] + ',' + c2i[c2] + ',' + str(hop_dist_map[c1][c2]) + '\n')
f.write('route_table:\n')
for c1 in dist_map:
    for c2 in dist_map:
        f.write(c2i[c1] + ',' + c2i[c2] + ',' + c2i[route_table[c1][c2]] + '\n')
f.write('hop_num_map:\n')
for c1 in dist_map:
    for c2 in dist_map:
        f.write(c2i[c1] + ',' + c2i[c2] + ',' + str(hop_num_map[c1][c2]) + '\n')
f.close()