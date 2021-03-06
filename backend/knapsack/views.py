import json
import time
import datetime
import urlparse
import logging

import requests
from gcm import GCM
from pyramid.view import view_config
from sqlalchemy import and_, or_
from lockbox.lib import encrypt, decrypt

from .models import User, Page, DeviceToken, DATETIME_FORMAT


COOKIE_NAME = 'a'
VALIDATION_ENDPOINT = 'https://www.googleapis.com/oauth2/v3/tokeninfo'
EVENT_PAGE_ADD = 'pa'
SLOP = 10
PAGE_LIMIT = 100

log = logging.getLogger(__name__)


class Token(object):
    def __init__(self, **kwargs):
        self.params = {}
        for k, v in kwargs.items():
            self.add_item(k, v)

    def add_item(self, k, v):
        self.params[k] = v

    def update(self, d):
        self.params.update(d)

    def serialize(self, key):
        return encrypt(key, json.dumps(self.params, sort_keys=True))

    @classmethod
    def unserialize(cls, key, s):
        params = json.loads(decrypt(key, s))
        return cls(**params)


class AuthToken(Token):

    @classmethod
    def create(cls, email, expires_in, ts):
        return cls(em=email, ex=expires_in, ts=ts)

    @property
    def email(self):
        return self.params["em"]

    @property
    def expires_in(self):
        return self.params["ex"]

    @property
    def timestamp(self):
        return self.params["ts"]

    @property
    def expires_at(self):
        return float(self.timestamp) + float(self.expires_in) - SLOP

    @property
    def is_valid(self):
        return self.expires_at > time.time()


def get_api_key(lockbox):
    return lockbox.get("gcm_api_key")


def get_auth_secret(lockbox):
    return lockbox.get("auth_secret")


def body(d):
    return {k: v for k, v in d.items() if v is not None}


def error(message=None, **kwargs):
    rv = {'status': 'error', 'message': message}
    rv.update(kwargs)
    return body(rv)


def ok(**kwargs):
    rv = {'status': 'ok'}
    rv.update(kwargs)
    return body(rv)


def event(type_, **kwargs):
    rv = {'t': type_}
    rv.update(kwargs)
    return body(rv)


def check_token(auth_token):
    resp = requests.post(VALIDATION_ENDPOINT, params={'access_token': auth_token}).json()

    if resp.get('error'):
        raise ValueError('invalid auth_token')

    email = resp.get('email')
    expires_in = resp.get('expires_in')

    if not email:
        raise ValueError('couldnt get email')

    return email, expires_in


def validate_auth_token(f):
    def _validate_auth_token(request, *args, **kwargs):
        token = None
        cookie = request.cookies.get(COOKIE_NAME)
        if cookie:
            try:
                token = AuthToken.unserialize(get_auth_secret(request.lockbox), cookie)
                if not token.is_valid:
                    token = None
            except Exception as e:
                log.exception("error decoding auth token: %s", str(e))

        if not token:
            auth_token = request.headers.get('Auth')
            if auth_token:
                try:
                    email, expires_in = check_token(auth_token)
                    token = AuthToken.create(email, expires_in, time.time())
                except ValueError as e:
                    return error(str(e))

        if not token:
            return error("no auth")

        request.response.set_cookie(COOKIE_NAME, token.serialize(get_auth_secret(request.lockbox)))

        user = find_or_create_user(request.session, token.email)

        return f(request, user, *args, **kwargs)

    return _validate_auth_token


# XXX
def send_welcome(user):
    pass


def find_or_create_user(session, email):
    user = session.query(User).filter_by(email=email).first()
    if not user:
        user = User(email=email)
        session.add(user)
        session.commit()

        send_welcome(user)

    return user


def upsert_page(session, user, url, params):
    page = user.pages.filter_by(url=url).first()

    created = False
    if not page:
        page = Page(
            url=url,
            user=user
        )
        session.add(page)
        created = True

    title = params.get("title")
    if title and title != page.title:
        page.title = title

    read = params.get("read")
    if read is not None and read != page.read:
        page.read = read

    if page.deleted:
        page.deleted = False

    if created or session.is_modified(page):
        session.commit()

    return page, created


def find_or_create_device_token(session, user, token, model=None):
    device_token = user.device_tokens.filter_by(token=token).first()

    if not device_token:
        device_token = DeviceToken(user=user, token=token)
        session.add(device_token)

    device_token.user = user
    device_token.model = model
    session.commit()

    return device_token


def notify_devices(session, api_key, user, event_data):

    tokens = [device_token.token for device_token in user.active_device_tokens]
    if not tokens:
        return

    log.info("notifying device tokens %s", tokens)

    gcm = GCM(api_key)
    resp = gcm.json_request(registration_ids=tokens, data=event_data)

    if 'errors' in resp:
        for error, reg_ids in resp.items():
            if error is 'NotRegistered':
                query = and_(
                    DeviceToken.user == user,
                    DeviceToken.token.in_(reg_ids)
                )
                session.query(DeviceToken).filter(query).update({
                    'active': False, 'deactivated_on': datetime.datetime.utcnow()
                })

    if 'canonical' in resp:
        for reg_id, canonical_id in resp['canonical'].items():
            query = and_(
                DeviceToken.user == user,
                DeviceToken.token == reg_id
            )
            session.query(DeviceToken).filter(query).update({'token': canonical_id})


def user_pages_response(user, before=None, limit=PAGE_LIMIT):
    pages = user.active_pages
    if before:
        pages = pages.filter(Page.created < before)
    pages = pages.limit(limit + 1)

    pages = list(pages)
    before = None
    if len(pages) > limit:
        pages = pages[:-1]
        before = pages[-1].created.strftime(DATETIME_FORMAT)

    return ok(pages=[page.to_dict() for page in pages], before=before)


def valid_page_url(url):
    parsed_url = urlparse.urlparse(url)
    return parsed_url.scheme in ["http", "https"] and parsed_url.netloc


@view_config(route_name='pages', renderer='json', request_method="POST")
@validate_auth_token
def update_page(request, user):

    params = json.loads(request.body)

    if not all(params.get(i) for i in ['page']):
        return error('invalid page')

    url = params["page"]["url"]
    if not valid_page_url(url):
        return error("URL [{}] is not valid".format(url))

    page, created = upsert_page(request.session, user, url, params)
    if created:
        notify_devices(request.session, get_api_key(request.lockbox), user, event(EVENT_PAGE_ADD, url=page.url))

    return ok(page=page.to_dict())


def parse_datetime(s):
    return datetime.datetime.strptime(s, DATETIME_FORMAT) if s else None


@view_config(route_name='pages', renderer='json', request_method="GET")
@validate_auth_token
def list_pages(request, user):
    before = parse_datetime(request.params.get("before"))
    return user_pages_response(user, before=before)


@view_config(route_name='pages', renderer='json', request_method="DELETE")
@validate_auth_token
def delete_page(request, user):

    params = json.loads(request.body)

    if not any(params.get(i) for i in ['url', 'uid']):
        return error('invalid page')

    parts = [Page.url == params['url']]

    if 'uid' in params:
        parts.append(Page.uid == params['uid'])

    criteria = and_(Page.user == user, or_(*parts))
    request.session.query(Page).filter(criteria).update({'deleted': True})
    request.session.commit()
    return ok()


@view_config(route_name='device_tokens', renderer='json', request_method="POST")
@validate_auth_token
def add_device_token(request, user):

    params = json.loads(request.body)

    if not all(params.get(i) for i in ['device_token']):
        return error('invalid device token')

    find_or_create_device_token(request.session, user, params.get('device_token'), params.get('model'))

    return ok()


@view_config(route_name='root', renderer='json')
def root(request):
    return ok()
