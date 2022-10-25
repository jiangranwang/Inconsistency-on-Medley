import numpy as np
import matplotlib
import matplotlib.pyplot as plt


matplotlib.rc('hatch', linewidth=1)
matplotlib.rc('patch', linewidth=1.8)
matplotlib.rc('font', family='Times New Roman', weight='bold', size=12)
matplotlib.rc('axes', labelweight='bold')
matplotlib.rc('figure', titleweight='bold')
matplotlib.rc('mathtext', fontset='stix')

# bar_color = ['#ffffff', '#df8244', '#4ead5b', '#b99130', '#e7e6e6']
# bar_color = {'naive': '#ffffff', 'active': '#df8244', 'passive': '#4ead5b', 'act_pas': '#b99130'}
bar_color = {'naive': '#ffffff', 'active': '#ffffff', 'passive': '#ffffff', 'act_pas': '#ffffff'}
# bar_edge_color = ['#3f74b1', '#ffffff', '#3EAD5B', '#ffffff', '#000000']
# bar_edgecolor = {'naive': '#2a80ba', 'active': '#4b0082', 'passive': '#026440', 'act_pas': '#a50000'}
bar_edgecolor = {'naive': '#3B568E', 'active': '#AE6437', 'passive': '#7F7F7F', 'act_pas': '#8CAA65'}
# bar_pattern = ['///', '.', '\\', '+']
bar_pattern = {'naive': '//...', 'active': 'xx...', 'passive': '\\\\...', 'act_pas': '+...'}
bar_pattern_dis = {'naive': '//', 'active': 'xx', 'passive': '\\\\', 'act_pas': '+'}
bar_pattern_incp = {'naive': '...', 'active': 'oo', 'passive': '\\\\||', 'act_pas': ''}
line_pattern = {'naive': '--', 'active': '-', 'passive': ':', 'act_pas': '-.'}
line_legend = {'naive': 'Medley', 'active': 'active', 'passive': 'passive', 'act_pas': 'act & pas'}
line_marker = {'naive': '+', 'active': 'x', 'passive': '.', 'act_pas': '^'}
line_color = {'naive': '#3B568E', 'active': '#AE6437', 'passive': '#7F7F7F', 'act_pas': '#8CAA65'}



# def get_bar_color(i, stacked=False):
# 	return bar_color[i % len(bar_color)]

# def get_edge_color(i):
# 	return bar_edge_color[i % len(bar_edge_color)]

# def get_pattern(i):
# 	return bar_pattern[i % len(bar_pattern)]

def get_bar_color(bar, stacked=False):
	return bar_color.get(bar, 'k')

def get_edge_color(bar):
	return bar_edgecolor.get(bar, 'k')

def get_pattern(bar, dis=False):
	if dis == 2:
	   return bar_pattern_incp.get(bar, '/')
	elif dis:
		return bar_pattern_dis.get(bar, '/')
	return bar_pattern.get(bar, '/')

def get_line_pattern(line):
	return line_pattern.get(line, '-')

def get_line_marker(line):
	return line_marker.get(line, '+')

def get_line_legend(line, with_m=False):
	if with_m:
		return line_legend.get(line, line) + ' (m=3)'
	return line_legend.get(line, line)

def background_setting():
	plt.grid(axis='y', zorder=1)
	plt.tick_params(bottom=False, top=False, left=False, right=False)
