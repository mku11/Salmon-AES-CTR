from tasktiger import TaskTiger
from tasktiger._internal import ERROR
from tasktiger.task import Task

# states to retry
purge_states = [ERROR]

def retry():
    for state in purge_states:
        tiger = TaskTiger()
        _, tasks = Task.tasks_from_queue(
            tiger,
            "default",
            state,
            include_not_found=True,
        )

        for task in tasks:
            try:
                task.retry()
                print("retried:", task.id)
            except Exception as ex:
                print("cannot retry:", task.id, ex)


retry()
