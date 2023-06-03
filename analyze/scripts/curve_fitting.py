
import matplotlib.pyplot as plt 
from scipy.optimize import curve_fit
import numpy as np
import json

NUM_NODES = 256
CUTOFF_RDS = 360000
CONDENSE_FACTOR = 8
KERNEL_SIZE = 5
# MINNEST = False # min ever seen, or just the memlist itself over time?
MINNEST = True

FIT = True
# FIT = False

JUST_AVE = False
# JUST_AVE = True

PARSE = False

JSON_FILE = f"curvestore/tempstore_{CONDENSE_FACTOR}_{MINNEST}.json"

if not PARSE:
    try:
        with open(JSON_FILE,"r") as dfile:
            a = json.load(dfile)
            average_map = a['ave']
            min_map = a['min']
            max_map = a['max']
    except:
        print("Failed to load, defaulting to reading from file")
        PARSE = True

if PARSE:
    f = open("../256_350_records.csv")
    average_map = {}
    min_map = {}
    max_map = {}

    i = 0
    for line in f:
    # lines = f.readlines()
    # for line in lines[:NUM_NODES*CUTOFF_RDS]:
        items = line.split(",")
        # items[1] is the round number
        ind = int(items[1]) - int(items[1])%CONDENSE_FACTOR
        # ind = CONDENSE_FACTOR*((int(items[1]))//CONDENSE_FACTOR)
        if MINNEST:
            val = int(items[2])
        else:
            val = int(items[3])

        if ind in average_map:
            average_map[ind]+=(val/(NUM_NODES*CONDENSE_FACTOR))
        else:
            average_map[ind] = (val/(NUM_NODES*CONDENSE_FACTOR))

        if ind in min_map:
            min_map[ind] = min(val,min_map[ind])
        else:
            min_map[ind] = val

        if ind in max_map:
            max_map[ind] = max(val,max_map[ind])
        else:
            max_map[ind] = val

        i+=1
        if i >= NUM_NODES*CUTOFF_RDS:
            break

    # average_map now maps timestep to the average min then

    with open(JSON_FILE,"w") as ofile:
        json.dump({"ave":average_map,"min":min_map,"max":max_map},ofile)

# x axis values
x = []
# corresponding y axis values
average = []
mins = []
maxs = []

for key in average_map:
    x.append(float(key))
    average.append(average_map[key])
    mins.append(min_map[key])
    maxs.append(max_map[key])


if not MINNEST:
    # need to smooth
    kernel = np.ones(KERNEL_SIZE) / KERNEL_SIZE
    average = np.convolve(average, kernel, mode='same')
    maxs = np.convolve(maxs, kernel, mode='same')
    mins = np.convolve(mins, kernel, mode='same')

start_ind = 2
end_ind = -3
x = x[start_ind:end_ind]
average = average[start_ind:end_ind]
maxs = maxs[start_ind:end_ind]
mins = mins[start_ind:end_ind]


# print(type(x[-1]))

# plotting the points
plt.plot(x, average, color='red', label = "averages")
if not JUST_AVE:
    plt.plot(x, mins, label = "mins")
    plt.plot(x, maxs, label = "maxs")

# naming the x axis
plt.xlabel(f"Round Number")
# naming the y axis
plt.ylabel('# of Nodes')

# giving a title to my graph
if MINNEST:
    plt.title('Min Memlist Over Time (Random 256)')
else:
    plt.title('Memlist Over Time (Random 256)')


if FIT:
    def test(x,a,b,c):
        # return a*np.exp(b*x)
        return a*np.exp(b*x) + c

    # def test(x,a,b,c,d):
    #     return a*x**2 + b*x + c
    #     # return a*x**3 + b*x**2 + c*x + d

    # print(type(x[-1]))
    param, param_cov = curve_fit(test,x,average,p0 = [1,-1/200,256])
    # print(type(x[-1]))
    print(param)
    # print(type(x[0]),x[0])
    # print(type(param[0]))
    x2 = np.array(x)
    # ans = (param[0]*x**3 + param[1]*x**2 + param[2]*x + param[3])
    # ans = (param[0]*x**2 + param[1]*x + param[2])
    ans = (param[0]*np.exp(param[1]*x2) + param[2])

    plt.plot(x,ans,color='brown',label="fitted",linewidth=2)

plt.plot(x,[128]*len(x),label="required")
plt.ylim(ymin=0)

# function to show the plot
print(x[-3:])
plt.legend()
# plt.xticks(x[::4000],rotation=-45)
# plt.xticks([i//8 for i in range(0,360000+1,20000)],labels=range(0,360000+1,20000),rotation=-45)
# plt.xticks(ticks=x[::4096],labels=range(0,350000+1,8*4096),rotation=-45)
# plt.show()
plt.savefig("memsize_over_time.png")


        