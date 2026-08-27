from pylab import *
import sys
from numpy import quantile, linspace

if __name__ == "__main__":
    # print(sys.argv[1])
    total_msgs, avg_bw = [], []
    for np in linspace(0.1, 1.0, 10):
        filename = sys.argv[1].split("np")[0] + "np" + str(np)[:3] + sys.argv[1].split("np")[1][3:]
        for seed in range(int(sys.argv[2])):
            with open(filename + "_" + str(seed) + ".csv", "r") as f:
                for line in f.readlines():
                    if line.split(",")[0] == "Total messages sent in the network":
                        total_msgs.append(int(line.split(",")[1]))
                    elif line.split(",")[0] == "Average bandwidth of messages sent in the network":
                        avg_bw.append(float(line.split(",")[1]))

    fig, axs = plt.subplots(1, 2, figsize=(6, 8))

    # plot the cumulative histogram
    axs[1].hist(total_msgs, density=True, histtype='step', cumulative=True)
    quant_5, quant_50, quant_95 = quantile(total_msgs, [0.05, 0.5, 0.95])
    axs[1].plot([quant_5]*1000, linspace(0, 1, 1000), 'r:')
    axs[1].plot(linspace(0, 70, 1000), [0.05]*1000, 'r:')
    axs[1].plot(quant_5, 0.05, 'rx')
    axs[1].text(quant_5+1, 0.02, str(int(quant_5)), color = 'r')
    axs[1].text(-30, 0.04, "0.05", color = 'r')
    axs[1].plot([quant_50]*1000, linspace(0, 1, 1000), 'g:')
    axs[1].plot(linspace(0, 70, 1000), [0.5]*1000, 'g:')
    axs[1].plot(quant_50, 0.5, 'gD')
    axs[1].text(quant_50+1, 0.47, str(int(quant_50)), color = 'g')
    axs[1].text(-30, 0.49, "0.50", color = 'g')
    axs[1].plot([quant_95]*1000, linspace(0, 1, 1000), 'm:')
    axs[1].plot(linspace(0, 70, 1000), [0.95]*1000, 'm:')
    axs[1].plot(quant_95, 0.95, 'mx')
    axs[1].text(quant_95+1, 0.92, str(int(quant_95)), color = 'm')
    axs[1].text(-30, 0.94, "0.95", color = 'm')
    axs[1].set_title('Total Messages')
    axs[0].hist(avg_bw, density=True, histtype='step', cumulative=True)
    quant_5, quant_50, quant_95 = quantile(avg_bw, [0.05, 0.5, 0.95])
    axs[0].plot([quant_5]*1000, linspace(0, 1, 1000), 'r:')
    axs[0].plot(linspace(0, 70, 1000), [0.05]*1000, 'r:')
    axs[0].plot(quant_5, 0.05, 'rx')
    axs[0].text(quant_5+1, 0.02, str(int(quant_5)), color = 'r')
    axs[0].text(-30, 0.04, "0.05", color = 'r')
    axs[0].plot([quant_50]*1000, linspace(0, 1, 1000), 'g:')
    axs[0].plot(linspace(0, 70, 1000), [0.5]*1000, 'g:')
    axs[0].plot(quant_50, 0.5, 'gD')
    axs[0].text(quant_50+1, 0.47, str(int(quant_50)), color = 'g')
    axs[0].text(-30, 0.49, "0.50", color = 'g')
    axs[0].plot([quant_95]*1000, linspace(0, 1, 1000), 'm:')
    axs[0].plot(linspace(0, 70, 1000), [0.95]*1000, 'm:')
    axs[0].plot(quant_95, 0.95, 'mx')
    axs[0].text(quant_95+1, 0.92, str(int(quant_95)), color = 'm')
    axs[0].text(-30, 0.94, "0.95", color = 'm')
    axs[0].set_title('Average Bandwidth among nodes')

    fig.legend(title=f"Changing node percentage\nSeed # = 10\nEpoch # = 100")

    print("Saving msgs plot at ", sys.argv[1].split("np")[0] + "np-variable" + sys.argv[1].split("np")[1][3:] + "_msgs.png")
    fig.savefig(sys.argv[1].split("np")[0] + "np-variable" + sys.argv[1].split("np")[1][3:] + "_msgs.png")

    total_msgs, avg_bw = [], []
    for k in [5, 10, 15, 20, 25]:
        filename = sys.argv[1].split("k")[0] + "k" + str(k) + sys.argv[1].split("k")[1][1:]
        for seed in range(int(sys.argv[2])):
            with open(filename + "_" + str(seed) + ".csv", "r") as f:
                for line in f.readlines():
                    if line.split(",")[0] == "Total messages sent in the network":
                        total_msgs.append(int(line.split(",")[1]))
                    elif line.split(",")[0] == "Average bandwidth of messages sent in the network":
                        avg_bw.append(float(line.split(",")[1]))

    fig, axs = plt.subplots(1, 2, figsize=(14, 8))

    # plot the cumulative histogram
    axs[1].hist(total_msgs, density=True, histtype='step', cumulative=True)
    quant_5, quant_50, quant_95 = quantile(total_msgs, [0.05, 0.5, 0.95])
    axs[1].plot([quant_5]*1000, linspace(0, 1, 1000), 'r:')
    axs[1].plot(linspace(0, 700000, 1000), [0.05]*1000, 'r:')
    axs[1].plot(quant_5, 0.05, 'rx')
    axs[1].text(quant_5+1, 0.02, str(int(quant_5)), color = 'r')
    axs[1].text(-150, 0.04, "0.05", color = 'r')
    axs[1].plot([quant_50]*1000, linspace(0, 1, 1000), 'g:')
    axs[1].plot(linspace(0, 700000, 1000), [0.5]*1000, 'g:')
    axs[1].plot(quant_50, 0.5, 'gD')
    axs[1].text(quant_50+1, 0.47, str(int(quant_50)), color = 'g')
    axs[1].text(-100, 0.49, "0.50", color = 'g')
    axs[1].plot([quant_95]*1000, linspace(0, 1, 1000), 'm:')
    axs[1].plot(linspace(0, 700000, 1000), [0.95]*1000, 'm:')
    axs[1].plot(quant_95, 0.95, 'mx')
    axs[1].text(quant_95+1, 0.92, str(int(quant_95)), color = 'm')
    axs[1].text(-100, 0.94, "0.95", color = 'm')
    axs[1].set_title('Total Messages')
    axs[0].hist(avg_bw, density=True, histtype='step', cumulative=True)
    quant_5, quant_50, quant_95 = quantile(avg_bw, [0.05, 0.5, 0.95])
    axs[0].plot([quant_5]*1000, linspace(0, 1, 1000), 'r:')
    axs[0].plot(linspace(0, 700000, 1000), [0.05]*1000, 'r:')
    axs[0].plot(quant_5, 0.05, 'rx')
    axs[0].text(quant_5+1, 0.02, str(int(quant_5)), color = 'r')
    axs[0].text(-100, 0.04, "0.05", color = 'r')
    axs[0].plot([quant_50]*1000, linspace(0, 1, 1000), 'g:')
    axs[0].plot(linspace(0, 700000, 1000), [0.5]*1000, 'g:')
    axs[0].plot(quant_50, 0.5, 'gD')
    axs[0].text(quant_50+1, 0.47, str(int(quant_50)), color = 'g')
    axs[0].text(-100, 0.49, "0.50", color = 'g')
    axs[0].plot([quant_95]*1000, linspace(0, 1, 1000), 'm:')
    axs[0].plot(linspace(0, 700000, 1000), [0.95]*1000, 'm:')
    axs[0].plot(quant_95, 0.95, 'mx')
    axs[0].text(quant_95+1, 0.92, str(int(quant_95)), color = 'm')
    axs[0].text(-100, 0.94, "0.95", color = 'm')
    axs[0].set_title('Average Bandwidth among nodes')

    fig.legend(title=f"Changing Κ\nSeed # = 10\nEpoch # = 100")

    print("Saving msgs plot at ", sys.argv[1].split("k")[0] + "k-variable" + sys.argv[1].split("k")[1][1:] + "_msgs.png")
    fig.savefig(sys.argv[1].split("k")[0] + "k-variable" + sys.argv[1].split("k")[1][1:] + "_msgs.png")