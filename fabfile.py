qimport os

import boto
from fabric.api import sudo, task
from fabric.contrib.files import append, exists
from fabric.contrib.project import rsync_project


VIRTUALENV = "/virtualenv"
SITE = "/site"

BASE_PACKAGES = [
    "curl",
    "python-software-properties",
    "emacs23-nox",
    "build-essential",
    "python-pip",
    "python-setuptools",
    "python-dev",
    "screen",
    "expect",
    "gdebi-core",
    "git"
]


def sysctl(setting):
    if sudo("sysctl -w {0}".format(setting), warn_only=True).succeeded:
        append("/etc/sysctl.conf", setting, use_sudo=True)


def apt_install(packages):
    sudo("apt-get install -y " + " ".join(packages))


def pip_install(packages, venv=False):
    cmd = "{}pip install {}".format(VIRTUALENV + "/bin/" if venv else "", " ".join(packages))
    sudo(cmd)


def configure_virtualenv(user):
    bashrc = "/home/{0}/.bashrc".format(user)
    if exists(bashrc):
        venv = "if [ -f /virtualenv/bin/activate ]; then . /virtualenv/bin/activate; fi"
        append(bashrc, venv, use_sudo=True)


@task
def create_stack(name, template_path, tags=None):
    with open(template_path) as f:
        template = f.read()

    conn = boto.connect_cloudformation()
    conn.create_stack(name, template_body=template, tags=tags)


@task
def update_system():
    sudo("apt-get update && apt-get -y upgrade")


def slurp_lines(path):
    with open(path) as f:
        return [line.strip() for line in f]


def site_packages():
    try:
        return slurp_lines("site-packages.txt")
    except IOError:
        pass
    return []


def install_ntp():
    sudo('cp /usr/share/zoneinfo/UTC /etc/localtime')
    sysctl("xen.independent_wallclock=1")
    apt_install(["ntp"])


@task
def bootstrap():
    update_system()

    install_ntp()

    apt_install(BASE_PACKAGES + site_packages())
    pip_install(["virtualenv"])

    if not exists(VIRTUALENV):
        sudo("/usr/local/bin/virtualenv --no-site-packages {0}".format(VIRTUALENV))

    configure_virtualenv("ubuntu")

    if os.path.exists("requirements.txt"):
        packages = slurp_lines("requirements.txt")
        pip_install(packages, venv=True)

@task
def deploy(name):
    rsync_project()
