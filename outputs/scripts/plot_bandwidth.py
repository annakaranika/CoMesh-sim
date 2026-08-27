import matplotlib.pyplot as plt
import numpy as np
import sys
import plot_setting

def setBoxColors(bp, color):
    for item in ['boxes', 'whiskers', 'medians', 'caps']:
        plt.setp(bp[item], color=color)
    # bp['fliers'].set_markeredgecolor(color)

if __name__ == "__main__":
    metrics = ['e2e', 'e2e_bg', 'e2e_fg', 'h2h', 'h2h_bg', 'h2h_fg']
    routine_nos = [2 ** r for r in range(0, 5)]
    seed_no = int(sys.argv[2])
    device_no = int(sys.argv[1].split('_d')[1].split('_grid')[0])
        
    labels = [str(seed) for seed in range(seed_no)]
    x = np.arange(len(labels)) # the label locations
    width = 0.25 # the width of the bars
    
    for r in routine_nos:
        bandwidth = {
            rNo: {
                metric: {
                    seed: {} for seed in range(seed_no)
                } for metric in metrics
            } for rNo in routine_nos
        }
        bandwidth_filename = sys.argv[1].split("_r")[0] + '_r' + str(r) + '_a' +  sys.argv[1].split("_a")[1][:-5]
        
        for seed in range(seed_no):
            node_list_filename = 'workloads/node_list/node_list' + sys.argv[1].split("bandwidth")[2].split('_grid')[0] + '_np' + sys.argv[1].split('_np')[1].split('_cp')[0] + '_s' + str(seed) + '.txt'
            with open(node_list_filename, 'r') as f:
                node_list = f.read().split()
                for node in node_list:
                    for metric in metrics:
                        bandwidth[r][metric][seed][node] = 0
            for d in range(device_no):
                for metric in metrics:
                    bandwidth[r][metric][seed][str(d)] = 0

            with open(bandwidth_filename + str(seed) + ".csv", "r") as f:
                for line in f.readlines():
                    if line == "Src,Dst,E2EMsg,E2E_BG,E2E_FG,H2HMsg,H2H_BG,H2H_FG\n":
                        continue
                    
                    src = line.split(",")[0]
                    for i, metric in enumerate(metrics):
                        bandwidth[r][metric][seed][src] += int(line.split(",")[2 + i])

            for metric in metrics:
                bandwidth[r][metric][seed] = list(bandwidth[r][metric][seed].values())

        for metric in metrics:
            bandwidth[r][metric] = list(bandwidth[r][metric].values())

        fig, ax = plt.subplots(1, 2, figsize=(15, 6))
        bandwidth_bps = {}
        handles = {}
        for metric in metrics:
            if metric[-2:] == 'bg':
                positions = x
            elif metric[-2:] == 'fg':
                positions = x + width
            else:
                positions = x - width
            
            if metric[:3] == 'e2e':
                axes = ax[0]
            else:
                axes = ax[1]
            bandwidth_bps[metric] = axes.boxplot(bandwidth[r][metric], positions = positions, widths = width)

            if metric[-2:] == 'bg':
                setBoxColors(bandwidth_bps[metric], "green")
                handles[metric], = plt.plot([1,1],'g-')
            elif metric[-2:] == 'fg':
                setBoxColors(bandwidth_bps[metric], "red")
                handles[metric], = plt.plot([1,1],'r-')
            else:
                setBoxColors(bandwidth_bps[metric], "blue")
                handles[metric], = plt.plot([1,1],'b-')

        # Add some text for labels, title and custom x-axis tick labels, etc.
        # fig.suptitle('Communication Balancing\nRoutine # = ' + str(r))
        fig.supylabel('Sent messages')
        fig.supxlabel('Seed #')
        
        ax[0].set_xticks(x)
        ax[0].set_xticklabels(labels)
        ax[1].set_xticks(x)
        ax[1].set_xticklabels(labels)

        ax[0].legend((handles["e2e"], handles["e2e_bg"], handles["e2e_fg"]), ("All", "Background", "Foreground"), title="End to end")
        ax[1].legend((handles["h2h"], handles["h2h_bg"], handles["h2h_fg"]), ("All", "Background", "Foreground"), title="Hop to hop")

        for metric in metrics:
            handles[metric].set_visible(False)

        fig.tight_layout()

        plt.show()

        fig_filename = 'outputs/bandwidth_figures/bandwidth' + sys.argv[1].split('bandwidth')[2].split('_r')[0] + '_r' + str(r) + '_a' + sys.argv[1].split('bandwidth')[2].split('_a')[1][:-4] + ".png"
        print("Saving bandwidth plot at", fig_filename)
        fig.savefig(fig_filename)