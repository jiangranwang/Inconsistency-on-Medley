import matplotlib.pyplot as plt
import numpy as np
import matplotlib.cm as cm
import copy

granularity = 1000
num_nodes = 49

def plot_fps(xs, fps, prefix):
	fps_plot = {'1st': [], '2nd': [], '3rd': []}
	for fp in fps:
		fp_sorted = sorted([(v, i) for i, v in enumerate(fp)], reverse=True)
		fps_plot['1st'].append(fp_sorted[0])
		fps_plot['2nd'].append(fp_sorted[1])
		fps_plot['3rd'].append(fp_sorted[2])

	for key in fps_plot:
		plt.figure(figsize=(15,9))
		plt.plot(xs, [v[0] for v in fps_plot[key]])
		last = -1
		for i, v in enumerate(fps_plot[key]):
			if v[0] == 0:
				last = 0
				continue
			if last == v[0]:
				continue
			last = v[0]
			plt.text(x=i*granularity, y=v[0], s=v[1])
		plt.grid()
		plt.savefig(prefix + '_' + key + '.png')
		plt.close()

	plt.figure(figsize=(15,9))
	legends = []
	for key in fps_plot:
		plt.plot(xs, [v[0] for v in fps_plot[key]])
		legends.append(key)
	plt.legend(legends)
	plt.savefig(prefix + '_overlay.png')
	plt.close()

def single(fname):
	f = open(fname, 'r')
	lines = f.readlines()

	x_axis = []
	fp = [0 for _ in range(num_nodes)]
	suspect = [set() for _ in range(num_nodes)]
	unlucky = [0 for _ in range(num_nodes)]
	fps = []
	suspects = []
	unluckys = []

	idx = 0
	while lines[idx][:7] != 'INFO 0:':
		idx += 1
		continue
	time = 0
	while idx < len(lines):
		t = time
		x_axis.append(time)
		while idx < len(lines) and t < time + granularity:
			l = lines[idx]
			idx += 1
			ts = l.split(':')[0].split()[1]
			try:
				t = int(''.join(ts.split(',')))
			except:
				continue

			if 'as FAILED' in l:
				i = l.find('\"port\":')
				node = int(l[i+8:].split(',')[0])
				fp[node] += 1
			elif 'from FAILED to ACTIVE' in l:
				i = l.find('\"port\":')
				node = int(l[i+8:].split(',')[0])
				fp[node] -= 1
			elif '. SUSPECT' in l:
				node = int(l.split()[-2][:-1])
				pinger = int(l.split()[3])
				suspect[node].add(pinger)
			elif 'from ACTIVE to SUSPECTED' in l and 'Unexpected' not in l:
				i = l.find('\"port\":')
				node = int(l[i+8:].split(',')[0])
				pinger = int(l.split()[2])
				suspect[node].add(pinger)
			elif 'from SUSPECTED to ACTIVE.' in l:
				i = l.find('\"port\":')
				node = int(l[i+8:].split(',')[0])
				pinger = int(l.split()[2])
				suspect[node].remove(pinger)
			elif 'unlucky. Starting pinging it...' in l:
				node = int(l.split()[6])
				unlucky[node] += 1
			elif 'find itself unreported unlucky' in l:
				node = int(l.split()[2])
				unlucky[node] += 1
		fps.append([v for v in fp])
		suspects.append(copy.deepcopy(suspect))
		unluckys.append([v for v in unlucky])
		time += granularity

	return np.asarray(x_axis), np.asarray(fps), suspects, np.asarray(unluckys)

def get_coordinates(fn):
	f = open(fn, 'r')
	lines = f.readlines()
	return np.asarray([[float(c.split(',')[0]), float(c.split(',')[1])] for c in lines])

def plot_colored_map(coordinates, size, radius, arr, fn):
	plt.figure(figsize=(15,10))
	plt.xlim(-1, size+1)
	plt.ylim(-1, size+1)
	for c1 in coordinates:
		for c2 in coordinates:
			if (c1 == c2).all():
				continue
			if np.linalg.norm(c1 - c2) <= radius:
				plt.plot([c1[0], c2[0]], [c1[1], c2[1]], 'blue', zorder=0, alpha=0.25)
	for i in range(len(arr)):
		plt.annotate(int(arr[i]), coordinates[i], zorder=2)

	plt.scatter(coordinates.T[0], coordinates.T[1], zorder=1, s=500, c=arr, cmap='viridis')
	plt.xlabel('x axis')
	plt.ylabel('y axis')
	plt.colorbar()
	plt.savefig(fn)
	plt.close()

def multi(runFns, topoFn, prefix, size=15, radius=4):
	fp_freqs = [[] for _ in range(num_nodes)]
	fps = [0 for _ in range(num_nodes)]
	suspects = [0 for _ in range(num_nodes)]
	suspect_details = []
	unluckys = [0 for _ in range(num_nodes)]
	for fn in runFns:
		x_axis, fp, suspect_detail, unlucky = single(fn)
		suspect_details.append(suspect_detail)
		suspect = np.asarray([[len(v) for v in arr] for arr in suspect_detail])
		sorted_fps = np.asarray([sorted(sfp, reverse=True) for sfp in fp])
		fp_freqs = [fp_freqs[i] + list(sorted_fps.T[i]) for i in range(num_nodes)]
		fps = [fps[i] + sum(fp.T[i]) for i in range(num_nodes)]
		suspects = [suspects[i] + np.mean(suspect.T[i]) for i in range(num_nodes)]
		unluckys = [unluckys[i] + unlucky[-1][i] for i in range(num_nodes)]
	fp_freqs_mean = np.mean(fp_freqs, axis=-1)
	fp_freqs_max = np.max(fp_freqs, axis=-1)
	fps = np.asarray(fps)
	suspects = np.asarray(suspects)
	unluckys = np.asarray(unluckys)

	ps = range(3, 8)
	ks = range(3, 8)
	ts = min([len(arr) for arr in suspect_details])
	vals = {p: [[0 for _ in range(len(suspect_details))] for _ in range(ts)] for p in ps}
	global_suspect = [0 for _ in range(num_nodes)]
	local_suspect = [[0 for _ in range(num_nodes)] for _ in range(num_nodes)]
	sizes = {}
	
	for f in range(len(suspect_details)):
		print(prefix + '_' + str(f))
		for t in range(ts):
			for i in range(num_nodes):
				for j in suspect_details[f][t][i]:
					local_suspect[j][i] += 1
				global_suspect[i] += len(suspect_details[f][t][i])
			for p in ps:
				global_ids = set(np.argsort(global_suspect)[::-1][:p])
				diffs = []
				for arr in local_suspect:
					local_ids = set(np.argsort(arr)[::-1][:p])
					diffs.append(p - len(global_ids.intersection(local_ids)))
				vals[p][t][f] = np.mean(diffs)
			for k in ks:
				sample_size = k * 10
				if k not in sizes:
					sizes[k] = {}
				for p in ps:
					if p not in sizes[k]:
						sizes[k][p] = []
					curr = []
					for _ in range(sample_size):
						ids = np.random.choice(range(num_nodes), size=k)
						s = set()
						for i in ids:
							s = s.union(set(np.argsort(local_suspect[i])[::-1][:p]))
						curr.append(len(s))
					sizes[k][p].append(np.mean(curr))

	plt.figure()
	plt.xlabel('inconsistency value k')
	plt.ylabel('top #suspect sent p')
	plt.title('average number of unioned top #suspect')
	plt.scatter(list(ps) * len(ks), sum([[k for _ in range(len(ps))] for k in ks], []))
	for k in ks:
		for p in ps:
			plt.annotate(round(np.mean(sizes[k][p]), 2), [k, p])
	plt.grid()
	plt.savefig(prefix + '_ks_ps.png')
	plt.close()

	aggregated = []
	plt.figure()
	plt.xlabel('time')
	plt.ylabel('average difference of top suspects between global and local')
	for p in ps:
		curr = [np.mean(val) for val in vals[p]]
		aggregated.append([p, np.mean(curr)])
		plt.plot(x_axis, curr)
	aggregated = np.asarray(aggregated).T
	plt.grid()
	plt.legend(['p=' + str(p) for p in ps])
	plt.savefig(prefix + '_top_suspect_diffs.png')
	plt.close()

	plt.figure()
	plt.xlabel('number of top suspects considered')
	plt.ylabel('average difference of suspects between global and local')
	plt.bar(aggregated[0], aggregated[1])
	plt.savefig(prefix + '_aggregated_suspect_diffs.png')
	plt.close()

	# skip plotting
	return 

	coordinates = get_coordinates(topoFn)
	plot_colored_map(coordinates, size, radius, fps, prefix + '_fps.png')
	plot_colored_map(coordinates, size, radius, suspects, prefix + '_suspects.png')
	plot_colored_map(coordinates, size, radius, unluckys, prefix + '_unluckys.png')

	# scatter plots
	plt.figure()
	plt.scatter(fps, suspects)
	plt.title('fps & suspects')
	plt.xlabel('fps')
	plt.ylabel('suspects')
	plt.savefig(prefix + '_fps_suspects.png')
	plt.close()
	plt.figure()
	plt.scatter(fps, unluckys)
	plt.title('fps & unluckys')
	plt.xlabel('fps')
	plt.ylabel('unluckys')
	plt.savefig(prefix + '_fps_unluckys.png')
	plt.close()

	# inconsistency plots
	limit = 5
	plt.figure()
	plt.bar(range(limit), fp_freqs_mean[:limit])
	plt.title('top m inconsistency & inconsistency value')
	plt.xlabel('top m inconsistency')
	plt.ylabel('inconsistency mean')
	plt.savefig(prefix + '_inconsistency_mean.png')
	plt.close()
	plt.figure()
	plt.bar(range(limit), fp_freqs_max[:limit])
	plt.title('top m inconsistency & inconsistency value')
	plt.xlabel('top m inconsistency')
	plt.ylabel('inconsistency max')
	plt.savefig(prefix + '_inconsistency_max.png')
	plt.close()
	plt.figure()
	for order in range(3):
		hist, bins = np.histogram(fp_freqs[order], bins=num_nodes)
		width = bins[1] - bins[0]
		plt.bar(bins[1:-1], hist[1:], align='center', width=width)
		plt.title('order_' + str(order) + '_inconsistency')
		plt.xlabel('inconsistency value')
		plt.ylabel('frequency')
		plt.savefig(prefix + '_order_' + str(order) + '_inconsistency.png')
		plt.close()

if __name__ == '__main__':
	# single runs
	# xs, fps, suspects, unluckys = single('runs/grid_base.log')
	# plot_fps(xs, fps, 'grid_base')
	# xs, fps, suspects, unluckys = single('runs/random_base.log')
	# plot_fps(xs, fps, 'random_base')
	# xs, fps, suspects, unluckys = single('runs/cluster_base.log')
	# plot_fps(xs, fps, 'cluster_base')

	# multiple runs
	gridFns = ['runs/grid/run_' + str(i) + '.log' for i in range(1, 101)]
	multi(gridFns, '../../bin/grid49.txt', 'grid')
	randomFns = ['runs/random/run_' + str(i) + '.log' for i in range(1, 101)]
	multi(randomFns, '../../bin/random49.txt', 'random')
	clusterFns = ['runs/cluster/run_' + str(i) + '.log' for i in range(1, 101)]
	multi(clusterFns, '../../bin/cluster49.txt', 'cluster')