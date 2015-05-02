import json
import pprint
from time import sleep

import boto
from fabric.api import task
from config import STACK_NAME, STACK


def existing_stacks(c):
    return [s.stack_name for s in c.describe_stacks()]


def deploy_stack(conn, name, stack):
    if name not in existing_stacks(conn):
        conn.create_stack(name, template_body=json.dumps(stack))
    else:
        conn.update_stack(name, template_body=json.dumps(stack))


def wait_for_stack(conn, name, timeout=10):
    while True:
        rv = conn.describe_stacks(name)
        print rv[0].stack_status
        if rv[0].stack_status.endswith('_COMPLETE'):
            break
        sleep(timeout)

@task
def deploy():
    conn = boto.connect_cloudformation()
    deploy_stack(conn, STACK_NAME, STACK)
    wait_for_stack(conn, STACK_NAME)


def get_stack_outputs():
    conn = boto.connect_cloudformation()
    return {output.key: output.value for output in conn.describe_stacks(STACK_NAME)[0].outputs}


@task
def describe_stack():
    outputs = get_stack_outputs()
    pprint.pprint(outputs)

