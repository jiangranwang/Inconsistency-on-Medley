
import matplotlib.pyplot as plt 
NUM_NODES = 256
CUTOFF_RDS = 400000
CONDENSE_FACTOR = 10
# MINNEST = False # min ever seen, or just the memlist itself over time?
MINNEST = True 

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
    ind = (int(items[1]))//CONDENSE_FACTOR
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


# x axis values
x = []
# corresponding y axis values
average = []
mins = []
maxs = []

for key in average_map:
    x.append(key)
    average.append(average_map[key])
    mins.append(min_map[key])
    maxs.append(max_map[key])

x.pop()
average.pop()
mins.pop()
maxs.pop()


# plotting the points
plt.plot(x, average, label = "averages")
plt.plot(x, mins, label = "mins")
plt.plot(x, maxs, label = "maxs")
plt.plot(x, [128]*len(x),label="required")

# naming the x axis
plt.xlabel('Round Number (10s)')
# naming the y axis
plt.ylabel('# of Nodes')

# giving a title to my graph
if MINNEST:
    plt.title('Min Memlist Over Time (Random 256)')
else:
    plt.title('Memlist Over Time (Random 256)')
    

# function to show the plot
plt.legend()
plt.show()
plt.savefig("memsize_over_time.png")


        