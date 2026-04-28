from ci_workflow import sched

# sched("JS")
sched("JS",gpu=True)
print("ci workflow scheduled, execute run_worker.py script to start")