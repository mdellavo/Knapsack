import os

from pyramid.config import Configurator
from sqlalchemy import engine_from_config
from lockbox import open_lockbox, get_secret

from .models import (
    Session,
    Base,
)


def main(global_config, **settings):
    """ This function returns a Pyramid WSGI application.
    """

    engine = engine_from_config(settings, 'sqlalchemy.')
    Session.configure(bind=engine)
    Base.metadata.bind = engine

    config = Configurator(settings=settings)
    config.include('pyramid_mako')

    secret = os.environ["LOCKBOX_SECRET"] if "LOCKBOX_SECRET" in os.environ else get_secret()
    lockbox = open_lockbox(settings["lockbox_path"], secret)

    config.add_request_method(lambda request: lockbox, "lockbox", True, True)

    config.add_static_view(name='assets', path='knapsackbackend:assets')
    config.add_route('pages', '/pages')
    config.add_route('device_tokens', '/device_tokens')
    config.add_route('root', '/')
    config.scan()

    return config.make_wsgi_app()
