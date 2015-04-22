from fabric.api import sudo, task
from fabric.context_managers import settings
from fabric.contrib.files import append, exists
from fabric.operations import put

from config import USER, VIRTUALENV, DATABASE_NAME, BASE_PACKAGES, PIP_PACKAGES, NGINX_CONFIG, UWSGI_CONFIG


def sysctl(setting):
    if sudo("sysctl -w {0}".format(setting), warn_only=True).succeeded:
        append("/etc/sysctl.conf", setting, use_sudo=True)


def apt_install(packages):
    sudo("DEBIAN_FRONTEND=noninteractive apt-get install -y " + " ".join(packages))


def pip_install(packages, venv=False):
    cmd = "{}pip install {}".format(VIRTUALENV + "/bin/" if venv else "", " ".join(packages))
    sudo(cmd)


def configure_virtualenv(user):
    bashrc = "/home/{0}/.bashrc".format(user)
    if exists(bashrc):
        venv = "if [ -f /virtualenv/bin/activate ]; then . /virtualenv/bin/activate; fi"
        append(bashrc, venv, use_sudo=True)


@task
def update_system():
    sudo("DEBIAN_FRONTEND=noninteractive apt-get update && apt-get -y upgrade")


def install_ntp():
    sudo('cp /usr/share/zoneinfo/UTC /etc/localtime')
    sysctl("xen.independent_wallclock=1")
    apt_install(["ntp"])


@task
def install_pip():
    pip_install(["virtualenv"])

    if not exists(VIRTUALENV):
        sudo("/usr/local/bin/virtualenv --no-site-packages {0}".format(VIRTUALENV))

    configure_virtualenv(USER)

    pip_install(PIP_PACKAGES, venv=True)


@task
def install_apt():
    apt_install(BASE_PACKAGES)


@task
def create_database():
    with settings(warn_only=True):
        sudo("createuser {}".format(USER), user="postgres")
        sudo("createuser {}".format("www-data"), user="postgres")
        sudo("createdb {}".format(DATABASE_NAME), user="postgres")


@task
def configure_nginx():
    put(NGINX_CONFIG, "/etc/nginx/sites-available/default", use_sudo=True)


@task
def configure_uwsgi():
    put(UWSGI_CONFIG, "/etc/uwsgi/apps-enabled/knapsack.ini", use_sudo=True)


@task
def tail_uwsgi():
    sudo("tail -f -n 100 /var/log/uwsgi/app/knapsack.log")


@task
def tail_nginx():
    sudo("tail -f -n 100 /var/log/nginx/error.log  /var/log/nginx/access.log")


@task
def bootstrap():
    update_system()
    install_ntp()
    install_apt()
    install_pip()
    create_database()
    configure_nginx()
    configure_uwsgi()