import matplotlib.pyplot as plt
import numpy
import sys
import plot_setting

def setBoxColors(bp, color):
    for item in ['boxes', 'whiskers', 'fliers', 'medians', 'caps']:
        plt.setp(bp[item], color=color)

if __name__ == "__main__":
    def read_delays(filename, i):
        for seed in range(int(sys.argv[2])):
            with open(filename + "_" + str(seed) + ".csv", "r") as f:
                for line in f.readlines():
                    if line[0].isdigit():
                        try:
                            numbers = line.split(',')
                            let[i].append(int(numbers[0]))
                            qt[i].append(int(numbers[1]))
                            if len(numbers) > 2:
                                stt[i].append(int(numbers[2]))
                                akgt[i].append(int(numbers[3]))
                        except:
                            continue
                    # elif i == 0 and line.split(",")[0] == "Total messages sent in the network":
                    #     total_msgs.append(int(line.split(",")[1]))
                    #     print("added total messages")
                    # elif i == 0 and line.split(",")[0] == "Average bandwidth of messages sent in the network":
                    #     avg_bw.append(float(line.split(",")[1]))
    
    def draw_plots(labels, vname, png_filename, title):
        x = numpy.arange(3*len(labels), step=3) # the label locations
        width = 0.55 # the width of the bars

        # fig, ax = plt.subplots(figsize=(len(labels)*1.5, 5))
        fig, ax = plt.subplots(figsize=(7.5, 5))
        bps = {}
        bps["qt"] = ax.boxplot(qt, positions = x - width, widths = width) #, labels="Quorum Time")
        bps["let"] = ax.boxplot(let, positions = x, widths = width) #, labels="Leader Election Time")
        # bps["akgt"] = ax.boxplot(akgt, positions = x, widths = width) # , label="Broadcast+reply Time")
        bps["stt"] = ax.boxplot(stt, positions = x + width, widths = width) # , label="State Transfer Time")

        setBoxColors(bps["qt"], "green")
        setBoxColors(bps["let"], "blue")
        # setBoxColors(bps["akgt"], "orange")
        setBoxColors(bps["stt"], "red")

        # Add some text for labels, title and custom x-axis tick labels, etc.
        ax.set_ylabel('Delay (time units)')
        ax.set_xlabel(vname)
        # ax.set_title('Delays of communication within a k-group vs. ' + vname)
        ax.set_title(
            title + ", # Seeds = " + str(seed_no)
            # title + "# Seeds = " + str(seed_no) + "\n% Nodes = " + str(np)[:3]
                + ", Epoch length = " + sys.argv[1].split('_l')[4].split('_e')[0] + " tu",
            fontsize=14, y=1.1
        )
        ax.set_xticks(x)
        ax.set_xticklabels(labels)

        handles = {}
        handles["qt"], = plt.plot([1,1],'g-')
        handles["let"], = plt.plot([1,1],'b-')
        # handles["akgt"], = plt.plot([1,1],'-', color='orange')
        handles["stt"], = plt.plot([1,1],'r-')
        ax.legend(
            (handles["qt"], handles["let"], handles["stt"]),
            ('Quorum+Reply delay', 'Leader Election delay', 'State Transfer delay'),
            loc="lower center", bbox_to_anchor=(0.5, 1.0), ncol=3, fontsize=11
        )
        for handle in handles.values():
            handle.set_visible(False)

        fig.tight_layout()
        # plt.show()
        print("Saving in-k-group delay plot at", png_filename)
        fig.savefig(png_filename)

    seed_no = int(sys.argv[2])
    k = 5
    NPs = [0.05, 0.1, 0.2, 0.3, 0.4]
    # print(Ks, NPs)
    # total_msgs, avg_bw = [], []
    qt, let, akgt, stt = [[] for _ in NPs], [[] for _ in NPs], \
                            [[] for _ in NPs], [[] for _ in NPs]
    for i, np in enumerate(NPs):
        if np == 0.05:
            np_text = "0.05"
        else:
            str(np)[:3]
        filename = sys.argv[1].split("np")[0] + "np" + np_text \
                    + sys.argv[1].split("np")[1][3:].split("k")[0] \
                    + "k" + str(k) + sys.argv[1].split("k")[3][1:-6]
        read_delays(filename, i)

    vname = 'Node Percentage'
    png_filename = 'outputs/in_kgroup_figures/' \
                    + sys.argv[1].split('outputs/in_kgroup/')[1].split("np")[0] \
                    + 'np-variable_cp' + sys.argv[1].split("cp")[1].split("k")[0] \
                    + "k" + str(k) + sys.argv[1].split("k")[3][1:-6] + ".png"
    draw_plots(NPs, vname, png_filename, 'K=5 (f=2)')
    
    Fs = range(1, 6)
    Ks = [2*f+1 for f in Fs]
    np = 0.4
    qt, let, akgt, stt = [[] for _ in Ks], [[] for _ in Ks], \
                         [[] for _ in Ks], [[] for _ in Ks]
    for i, k in enumerate(Ks):
        filename = sys.argv[1].split("np")[0] + "np" + str(np)[:3] \
                    + sys.argv[1].split("np")[1][3:].split("k")[0] \
                    + "k" + str(k) + sys.argv[1].split("k")[3][1:-6]
        read_delays(filename, i)
    
    labels = [str(k) + ' (f=' + str(k//2) +')' for k in Ks]
    vname = 'K'
    png_filename = 'outputs/in_kgroup_figures/in_kgroup' \
                    + sys.argv[1].split('outputs/in_kgroup/in_kgroup')[1].split('_np')[0] \
                    + '_np' + str(np)[:3] \
                    + '_cp' + sys.argv[1].split("cp")[1].split("k")[0] \
                    + 'k-variable_' + sys.argv[1].split("_k")[3].split('_')[1] \
                    + '_' + sys.argv[1].split("_k")[3].split('_')[2] \
                    + '_' + sys.argv[1].split("_k")[3].split('_')[3] \
                    + '_l' + sys.argv[1].split('_l')[-1][:-6] + ".png"
    draw_plots(labels, vname, png_filename, f'Smart devices = {str(np*100)[:2]}%')
        
    k = 5
    np = 0.4
    policies = ['random', 'locality']
    clustering = {'random': 'rc', 'locality': 'lc'}
    selection = {'random': 'rs', 'locality': 'ls'}
    election = {'random': 'sh', 'locality': 'lsh'}
    qt, let, akgt, stt = [[] for _ in policies], [[] for _ in policies], \
                         [[] for _ in policies], [[] for _ in policies]
    for i, policy in enumerate(policies):
        filename = 'outputs/in_kgroup/in_kgroup_d250_'
        filename += sys.argv[1][len(filename):].split('_')[0] + '_' + clustering[policy] + '_'
        filename += sys.argv[1][len(filename):].split('_k')[0] + '_k' + str(k) + '_' \
                 + selection[policy] + '_' + election[policy] + '_sr' \
                 + sys.argv[1].split('_sr')[1][:-6]
        read_delays(filename, i)
    
    vname = 'Member & Leader Selection'
    png_filename = 'outputs/in_kgroup_figures/in_kgroup_' \
                    + sys.argv[1].split('outputs/in_kgroup/in_kgroup_')[1].split('_')[0] + '_' \
                    + sys.argv[1].split('outputs/in_kgroup/in_kgroup_')[1].split('_')[1] \
                    + '_np' + str(np)[:3] \
                    + '_cp' + sys.argv[1].split("cp")[1].split("k")[0] \
                    + 'k' + str(k) + '_' + sys.argv[1].split("_k")[3].split('_')[3] \
                    + '_l' + sys.argv[1].split('_l')[-1][:-6] + ".png"
    draw_plots(policies, vname, png_filename, f'K = {k}, Smart devices = {str(np*100)[:2]}%')
