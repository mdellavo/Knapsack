import json
import pprint

import boto
from fabric.api import task
from config import STACK_NAME, STACK

@task
def create_stack():
    conn = boto.connect_cloudformation()
    conn.create_stack(STACK_NAME, template_body=json.dumps(STACK))

@task
def update_stack():
    conn = boto.connect_cloudformation()
    conn.update_stack(STACK_NAME, template_body=json.dumps(STACK))


def get_stack_outputs():
    conn = boto.connect_cloudformation()
    return {output.key: output.value for output in conn.describe_stacks(STACK_NAME)[0].outputs}


@task
def describe_stack():
    outputs = get_stack_outputs()
    pprint.pprint(outputs)

