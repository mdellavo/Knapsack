from fabric.api import *
from fabric.contrib.project import rsync_project, os

HOST = 'knapsack.quuux.org'
PORT = 10220

env.hosts = [HOST]
env.port = PORT

SITE_DIR = os.path.join('/', 'home', 'marc')
LOG_DIR = os.path.join(SITE_DIR, 'logs')
DEPLOY_DIR = os.path.join(SITE_DIR, 'knapsack-backend')
ETC_DIR = os.path.join(SITE_DIR, 'etc')
NGINX_ETC_DIR = os.path.join(ETC_DIR, 'nginx')

ALL_DIRS = [LOG_DIR, ETC_DIR]

DEPLOY_SEQ = ['knapsack', 'nginx']
RESTART_SEQ = ['knapsack', 'nginx']

CONTAINERS = {
    'nginx': {
        'ports': {
            80: 80,
            443: 443
        },
        'links': {
            'knapsack': 'knapsack'
        },
        'volumes': {
            LOG_DIR: '/var/log',
            NGINX_ETC_DIR: '/etc/nginx'
        }
    },
    'knapsack': {
        'volumes': {
            LOG_DIR: '/var/log',
            DEPLOY_DIR: '/home/knapsack/knapsack-backend'
        },
    }
}


def virtualenv(env):
    return prefix('source %s/bin/activate' % env)


def get_container(name):
    return CONTAINERS[name]

FROM_GIT = False


def deploy_app():
    grunt()

    with settings(warn_only=True):
        for directory in ALL_DIRS:
            if run('test -d %s' % directory).failed:
                run('mkdir %s' % directory)

    put('deploy/nginx/configs/nginx.conf', 'etc/nginx')

    rsync_project('knapsack-backend', '.', ('build', 'node_modules', 'bower_components'))


def deploy_container(name):
    container = get_container(name)
    image = container.get('image', name)

    tag = 'knapsack/' + image
    filename = image + '.tar'

    local('docker build -t {0} deploy/{1}'.format(tag, image))
    local('docker save -o build/{0} {1}'.format(filename, tag))

    with settings(warn_only=True):
        if run('test -d build').failed:
            run('mkdir build')

    rsync_project('build/{0}'.format(filename), 'build/{0}'.format(filename))
    sudo('docker load -i build/{0}'.format(filename))


def start_container(name):
    container = get_container(name)
    image = container.get('image', name)
    tag = 'knapsack/{0}'.format(image)

    parts = ['docker', 'run', '-d', '--name', name]

    add_mapping = lambda arg, k, v: parts.extend([arg, '{0}:{1}'.format(k, v)])

    ports = container.get('ports', {})
    for port in ports:
        add_mapping('-p', port, ports[port])

    volumes = container.get('volumes', {})
    for volume in volumes:
        add_mapping('-v', volume, volumes[volume])

    links = container.get('links', {})
    for link in links:
        add_mapping('--link', link, links[link])

    parts.extend(['-h', name])

    parts.extend(['-t', tag])

    sudo(' '.join(parts))


def restart_container(name):
    with settings(warn_only=True):
        stop_container(name)
        remove_container(name)
    start_container(name)


def remove_container(name):
    sudo('docker rm {0}'.format(name))


def stop_container(name):
    sudo('docker stop {0}'.format(name))


def apply_containers(f):
    def _apply_containers():
        for container in CONTAINERS:
            f(container)

    return _apply_containers


start_containers = apply_containers(start_container)
stop_containers = apply_containers(stop_container)


def deploy_containers():
    for container in DEPLOY_SEQ:
        deploy_container(container)


def restart_containers():
    for containter in reversed(RESTART_SEQ):
        with settings(warn_only=True):
            stop_container(containter)
            remove_container(containter)

    for containter in RESTART_SEQ:
        start_container(containter)


def deploy():
    deploy_containers()
    deploy_app()
    restart_containers()


def grunt():
    local('grunt')