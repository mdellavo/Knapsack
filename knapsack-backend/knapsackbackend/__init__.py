from pyramid.config import Configurator
from sqlalchemy import engine_from_config

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
    config.add_route('pages', '/pages')
    config.add_route('device_tokens', '/device_tokens')
    config.scan()
    return config.make_wsgi_app()
