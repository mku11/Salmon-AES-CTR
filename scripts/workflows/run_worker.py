from tasktiger import TaskTiger

tiger = TaskTiger(setup_structlog=True)
tiger.run_worker(max_parallel_workers=1)