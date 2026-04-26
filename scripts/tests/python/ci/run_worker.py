from tasktiger import TaskTiger

# run up to 3 workers concurrently
tiger = TaskTiger(setup_structlog=True)
tiger.run_worker(max_parallel_workers=2)