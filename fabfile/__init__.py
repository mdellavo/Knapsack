import stack
import install
import deploy

from fabric.api import env, task

env.user = "ubuntu"

@task
def inspect():
    outputs = stack.get_stack_outputs()
    env.hosts = [outputs["PublicDNS"]]

@task
def hello_world():
    inspect()
    install.bootstrap()
    deploy.deploy()
    deploy.restart()