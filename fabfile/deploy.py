from fabric.api import task
from fabric.context_managers import settings, cd
from fabric.contrib.files import exists
from fabric.contrib.project import rsync_project
from fabric.operations import sudo

from config import SITE, PROJECT_DIR

STAGING = "~/knapsack-backend"
OLD = SITE + ".old"
NEW = SITE + ".new"

@task
def backend():
    rsync_project(STAGING, PROJECT_DIR + "/")

    if not exists(SITE):
        sudo("mkdir -p {}".format(SITE))

    for dirname in (OLD, NEW):
        if exists(dirname):
            sudo("rm -rf {}".format(dirname))

    sudo("cp -R {staging} {new}".format(staging=STAGING, new=NEW))
    sudo("chown -R www-data:www-data {}".format(NEW))

    with settings(warn_only=True):
        sudo("mv {site} {old}".format(site=SITE, old=OLD))

    sudo("mv {new} {site}".format(site=SITE, new=NEW))

    with cd(SITE):
        sudo("/virtualenv/bin/python {}/setup.py install".format(SITE))


@task
def createdb():
    with cd(SITE):
        sudo("/virtualenv/bin/initialize_knapsack-backend_db production.ini".format(SITE), user="www-data")


@task
def deploy():
    backend()
    createdb()


@task
def restart_nginx():
    sudo("/etc/init.d/nginx restart")


@task
def restart_uwsgi():
    sudo("/etc/init.d/uwsgi restart")


@task
def restart():
    restart_nginx()
    restart_uwsgi()