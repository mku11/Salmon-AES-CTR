from tasktiger import TaskTiger
import sys

def print_usage():
    print()
    print("python run_worker.py [options]")
    print("options:")
    print("-p: N parallel workers, default is 1")
    print()

print_usage()

workers=1
for i in range(len(sys.argv)):
    if sys.argv[i] == "-p":
        workers=int(sys.argv[i+1])

print(f"Starting workers: {workers}")
tiger = TaskTiger(setup_structlog=True)
tiger.run_worker(max_parallel_workers=workers)