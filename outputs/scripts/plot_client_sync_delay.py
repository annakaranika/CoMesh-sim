import matplotlib.pyplot as plt
import numpy as np
import sys
import plot_setting

def setBoxColors(bp, color):
    for item in ['boxes', 'whiskers', 'fliers', 'medians', 'caps']:
        plt.setp(bp[item], color=color)

if __name__ == "__main__":
    # print(sys.argv[1])
    seed_no = int(sys.argv[2])
    device_nos = [50, 250, 500, 750] #, 1000]
    # print(device_nos)
    device_topology_dims = ["5,5,2", "5,5,10", "5,10,10", "5,10,15"] #, "10,10,10"]
    
    client_delay = {"usr": [[] for _ in device_nos], "sys":  [[] for _ in device_nos], "ack":  [[] for _ in device_nos]}
    sync_delay = [[] for _ in device_nos]
    
    for i in range(0, len(device_nos)):
        client_delay_filename = 'outputs/client_delay/client_delay_d' + str(device_nos[i]) + "_grid" + device_topology_dims[i] + '_lc_np' + sys.argv[1].split("np")[1].split("_r")[0] + "_r1_a" + sys.argv[1].split("np")[1].split("_a")[1]
        for seed in range(seed_no):
            with open(client_delay_filename[:-6] + "_" + str(seed) + ".csv", "r") as f:
                for line in f.readlines():
                    if line == "RoutineID,SeqNo,ClientDelayUsr,ClientDelaySys,ClientDelayAck\n":
                        continue
                    client_delay["usr"][i].append(float(line.split(",")[2]))
                    client_delay["sys"][i].append(float(line.split(",")[3]))
                    client_delay["ack"][i].append(float(line.split(",")[4]))
        sync_delay_filename = 'outputs/sync_delay/sync_delay_d' + str(device_nos[i]) + "_grid" + device_topology_dims[i] + '_lc_np' + sys.argv[1].split("np")[1].split("_r")[0] + "_r1_a" + sys.argv[1].split("np")[1].split("_a")[1]
        for seed in range(seed_no):
            with open(sync_delay_filename[:-6] + "_" + str(seed) + ".csv", "r") as f:
                for line in f.readlines():
                    if line == "RoutineID,SeqNo,SyncDelay\n":
                        continue
                    if int(line.split(",")[2]) != -1:
                        sync_delay[i].append(float(line.split(",")[2]))


    labels = [str(dNo) for dNo in device_nos]
    x = np.arange(2*len(labels), step=2) # the label locations
    width = 0.75 # the width of the bars

    fig, ax = plt.subplots(figsize=(8, 4))
    client_delay_bps = {}
    # client_delay_bps["usr"] = ax.boxplot(client_delay["usr"], positions = x - 2 * width, widths = width) #, labels="Client delay (usr)")
    client_delay_bps["sys"] = ax.boxplot(client_delay["sys"], positions = x - width/2, widths = width) #, labels="Client delay (sys)")
    # client_delay_bps["ack"] = ax.boxplot(client_delay["ack"], positions = x, widths = width) # , label="Client delay (ack)")
    sync_delay_bps = ax.boxplot(sync_delay, positions = x + width/2, widths = width) # , label="Sync delay")

    # setBoxColors(client_delay_bps["usr"], "blue")
    setBoxColors(client_delay_bps["sys"], "green")
    # setBoxColors(client_delay_bps["ack"], "red")
    setBoxColors(sync_delay_bps, "orange")

    # Add some text for labels, title and custom x-axis tick labels, etc.
    ax.set_ylabel('Delay (time units)')
    ax.set_xlabel('Devices')
    # ax.set_title('Client and Synchronization delays vs. Devices')
    ax.set_xticks(x)
    ax.set_xticklabels(labels)

    client_delay_handles = {}
    # client_delay_handles["usr"], = plt.plot([1,1],'b-')
    client_delay_handles["sys"], = plt.plot([1,1],'g-')
    # client_delay_handles["ack"], = plt.plot([1,1],'r-')
    sync_delay_handle, = plt.plot([1,1],'-', color='orange')
    # ax.legend((client_delay_handles["usr"], client_delay_handles["sys"], client_delay_handles["ack"], sync_delay_handle),
            #   ('Client delay (usr)', 'Client delay (sys)', 'Client delay (ack)', 'Sync delay'),
    ax.legend((client_delay_handles["sys"], sync_delay_handle),
               ('Client delay', 'Sync delay'),
              title="Seeds = 10\nEpoch length = 200 tu")
    # client_delay_handles["usr"].set_visible(False)
    client_delay_handles["sys"].set_visible(False)
    # client_delay_handles["ack"].set_visible(False)
    sync_delay_handle.set_visible(False)

    fig.tight_layout()

    plt.show()

    print("Saving client/sync delay plot at", 'outputs/client_sync_delay_figures/client_sync_delay_grid_np' + sys.argv[1].split("np")[1].split("_r1")[0] + sys.argv[1].split("np")[1].split("_r1")[1][:-6] + ".png")
    fig.savefig('outputs/client_sync_delay_figures/client_sync_delay_grid_np' + sys.argv[1].split("np")[1].split("_r1")[0] + sys.argv[1].split("np")[1].split("_r1")[1][:-6] + ".png")