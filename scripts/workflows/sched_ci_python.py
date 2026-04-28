from ci_workflow import sched

# sched("PYTHON")
sched("PYTHON",gpu=True)
print("ci workflow scheduled, execute run_worker.py script to start")