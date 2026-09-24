import sys
import os
import subprocess

with open('experiments.txt', 'r') as inp, open('run02.csv', "w") as out:
    out.write("#Ver,thr,Gops/s\n")
    for line in inp:
        args = line.split()
        prog = ["..\\build\\install\\lab01\\bin\\lab01.bat", *args]
        print(prog)
        subprocess.run(prog, stdout=out)
