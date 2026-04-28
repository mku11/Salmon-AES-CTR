from ci_workflow import sched

# sched("JAVA")
# sched("CSHARP")
# sched("JS")
# sched("PYTHON")

sched("JAVA", gpu=True)
sched("CSHARP", gpu=True)
sched("JS", gpu=True)
sched("PYTHON", gpu=True)

print("ci workflow scheduled, execute run_worker.py script to start")