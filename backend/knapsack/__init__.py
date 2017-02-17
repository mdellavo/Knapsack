import os

from pyramid.config import Configurator
from lockbox import open_lockbox, get_secret

def get_lockbox(settings):
    secret = os.environ["LOCKBOX_SECRET"] if "LOCKBOX_SECRET" in os.environ else get_secret()
    lockbox = open_lockbox(settings["lockbox_path"], secret)
    return lockbox


def main(global_config, **settings):
    """ This function returns a Pyramid WSGI application.
    """

    config = Configurator(settings=settings)
    config.include('pyramid_mako')
    config.include('.models')

    lockbox = get_lockbox(settings)

    config.add_request_method(lambda request: lockbox, "lockbox", True, True)

    config.add_static_view(name='assets', path='knapsack:assets')
    config.add_route('pages', '/pages')
    config.add_route('device_tokens', '/device_tokens')
    config.add_route('root', '/')

    config.scan()

    return config.make_wsgi_app()
