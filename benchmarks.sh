#!/bin/zsh

mvn compiler:compile

mvn exec:java -Dexec.args="-e 0 -rn 0 -dn 9 -dts grid -dtd 3,3 -np 0.4 -cp 0.0 -nsd uniform -f 1 -el 100 -owd 5 -rd 9"