import matplotlib.pyplot as plt 
import numpy as np
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--file', dest='file', type=str, required=True)
parser.add_argument('--size', dest='size', type=int, required=True)
args = parser.parse_args()
file_name = args.file

with open(file_name, 'r') as f:
	lines = f.readlines()
	coordinates = np.asarray([[float(c.split(',')[0]), float(c.split(',')[1])] for c in lines])
	width = args.size
	length = args.size
	radius = 4
	title = file_name[:-4]
	plt.figure(figsize=(10,7))
	plt.xlim(0, width)
	plt.ylim(0, length)

	for c1 in coordinates:
		for c2 in coordinates:
			if (c1 == c2).all():
				continue
			if np.linalg.norm(c1 - c2) <= radius:
				plt.plot([c1[0], c2[0]], [c1[1], c2[1]], 'r', zorder=0, alpha=0.5)

	for i, c in enumerate(coordinates):
		plt.annotate(i, c, zorder=2)

	plt.scatter(coordinates.T[0], coordinates.T[1], zorder=1)
	plt.title(title)
	plt.xlabel('x axis')
	plt.ylabel('y axis')
	plt.savefig(file_name[:-4] + '.png')