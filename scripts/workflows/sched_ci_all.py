from ci_workflow import sched

sched("JAVA")
sched("CSHARP")
sched("JS")
sched("PYTHON")
print("ci workflow scheduled, execute run_worker.py script to start")