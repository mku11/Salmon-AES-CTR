from tasktiger import TaskTiger
from tasktiger._internal import COMPLETED, ERROR, WAITING
from tasktiger.task import Task

# states to purge
purge_states = [COMPLETED, ERROR, WAITING]

def purge():
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
                task.delete()
                print("deleted:", task.id)
            except Exception as ex:
                print("cannot delete:", task.id, ex)


purge()
