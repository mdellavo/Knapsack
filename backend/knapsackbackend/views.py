import json
import datetime
from gcm import GCM
from pyramid.view import view_config
import requests
from sqlalchemy import and_, or_

from .models import (
    Session,
    User,
    Page,
    DeviceToken
)

GET = 'GET'
POST = 'POST'
PUT = 'PUT'
DELETE = 'DELETE'

# XXX
API_KEY = 'AIzaSyBnoToB2rfo1wlkWi8-bbWTB9DPACiKb3Y'

VALIDATION_ENDPOINT = 'https://www.googleapis.com/oauth2/v1/tokeninfo'

EVENT_PAGE_ADD = 'pa'


def error(message=None, **kwargs):
    rv = {'status': 'error', 'message': message}
    rv.update(kwargs)
    return rv


def ok(**kwargs):
    rv = {'status': 'ok'}
    rv.update(kwargs)
    return rv


def event(type_, **kwargs):
    rv = {'t': type_}
    rv.update(kwargs)
    return rv


# XXX
def validate_auth_token_response(user, auth_token, issued_to, expires_in):
    return True


# XXX
def cache_auth_token(user, auth_token, expires_in):
    pass


def validate_auth_token(f):
    def _validate_auth_token(request, *args, **kwargs):

        auth_token = request.headers.get('Auth')

        if not auth_token:
            return error('invalid auth_token')

        resp = requests.post(VALIDATION_ENDPOINT, params={'access_token': auth_token}).json()

        if resp.get('error'):
            return error('invalid auth_token')

        # FIXME getting device_token not
        email = resp.get('email')
        expires_in = resp.get('expires_in')
        issued_to = resp.get('issued_to')

        user = find_or_create_user(email)
        if not validate_auth_token_response(user, auth_token, issued_to, expires_in):
            return error('invalid auth_token')

        cache_auth_token(user, auth_token, expires_in)

        return f(request, user, *args, **kwargs)

    return _validate_auth_token


# XXX
def send_welcome(user):
    pass


def find_or_create_user(email):
    user = Session.query(User).filter_by(email=email).first()
    if not user:
        user = User(email=email)
        Session.add(user)

        send_welcome(user)

    return user


def find_or_create_page(user, url):
    page = user.pages.filter_by(url=url).first()
    if not page:
        page = Page(
            url=url,
            user=user
        )
        Session.add(page)
    page.deleted = False
    return page


def find_or_create_device_token(user, token, model=None):
    device_token = user.device_tokens.filter_by(token=token).first()

    if not device_token:
        device_token = DeviceToken(token=token)
        Session.add(device_token)

    device_token.user = user
    device_token.model = model

    return device_token


def notify_devices(user, event_type):

    tokens = [device_token.token for device_token in user.active_device_tokens]
    if not tokens:
        return

    gcm = GCM(API_KEY)
    resp = gcm.json_request(registration_ids=tokens, data=event(event_type))

    if 'errors' in resp:
        for error, reg_ids in resp.items():
            if error is 'NotRegistered':
                query = and_(
                    DeviceToken.user == user,
                    DeviceToken.token.in_(reg_ids)
                )
                Session.query(DeviceToken).filter(query).update({
                    'active': False, 'deactivated_on': datetime.datetime.utcnow()
                })

    if 'canonical' in resp:
        for reg_id, canonical_id in resp['canonical'].items():
            query = and_(
                DeviceToken.user == user,
                DeviceToken.token == reg_id
            )
            Session.query(DeviceToken).filter(query).update({'token': canonical_id})

user_pages = lambda user: ok(pages=[page.to_dict() for page in user.active_pages])

@view_config(route_name='pages', renderer='json', request_method=POST)
@validate_auth_token
def add_page(request, user):

    params = json.loads(request.body)

    if not all(params.get(i) for i in ['page']):
        raise error('invalid page')

    find_or_create_page(user, params['page']['url'])

    # FIXME move off to work queue
    notify_devices(user, EVENT_PAGE_ADD)

    return user_pages(user)


@view_config(route_name='pages', renderer='json', request_method=PUT)
@validate_auth_token
def add_pages(request, user):
    params = json.loads(request.body)

    if not all(params.get(i) for i in ['pages']):
        raise error('invalid page')

    pages = params.get('pages')

    urls = set(page['url'] for page in pages if page.get('url'))
    known_urls = set(page.url for page in user.pages)
    missing_urls = urls - known_urls

    def make_page(page):
        return Page(
            user=user,
            url=page['url'],
            title=page.get('title'),
            read=page.get('read'),
            created=datetime.datetime.strptime(page['created'], '%Y-%m-%dT%H:%M:%S.%f') if page.get('created') else None
        )

    missing_pages = [make_page(page) for page in pages if page['url'] in missing_urls]
    Session.add_all(missing_pages)

    return user_pages(user)


@view_config(route_name='pages', renderer='json', request_method=GET)
@validate_auth_token
def list_pages(request, user):
    return user_pages(user)


@view_config(route_name='pages', renderer='json', request_method=DELETE)
@validate_auth_token
def delete_page(request, user):

    params = json.loads(request.body)

    if not any(params.get(i) for i in ['url', 'uid']):
        raise error('invalid page')

    parts = [Page.url == params['url']]

    if 'uid' in params:
        parts.append(Page.uid == params['uid'])

    criteria = and_(Page.user == user, or_(*parts))
    Session.query(Page).filter(criteria).update({'deleted': True})

    return user_pages(user)


@view_config(route_name='device_tokens', renderer='json', request_method=POST)
@validate_auth_token
def add_device_token(request, user):

    params = json.loads(request.body)

    if not all(params.get(i) for i in ['device_token']):
        raise error('invalid device token')

    find_or_create_device_token(user, params.get('device_token'), params.get('model'))

    return ok()


@view_config(route_name='root', renderer='root.mako')
def root(request):
    return {}