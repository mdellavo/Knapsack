import mock
import unittest
from Cookie import SimpleCookie

from knapsack import main, get_lockbox
from pyramid.paster import get_appsettings
from webtest import TestApp as WebTestApp

from knapsack.models import Base, User, Page, DeviceToken
from knapsack.views import AuthToken, COOKIE_NAME, get_auth_secret


class FunctionalTests(unittest.TestCase):
    maxDiff = None

    def setUp(self):

        self.settings = get_appsettings("development.ini#knapsack")
        self.settings['sqlalchemy.url'] = 'sqlite:///:memory:'

        app = main({}, **self.settings)
        self.app = WebTestApp(app)

        session_factory = app.registry['dbsession_factory']
        self.engine = session_factory.kw['bind']
        self.session = session_factory()

        Base.metadata.create_all(bind=self.engine)

    def tearDown(self):
        Base.metadata.drop_all(bind=self.engine)


AUTH_TOKEN, AUTH_EMAIL, AUTH_EXPIRES = "x" * 20, "foo@example.com", 600


def create_user(session, email):
    user = User(email=email)
    session.add(user)
    session.commit()
    return user


def create_page(session, user, url):
    page = Page(user=user, url=url)
    session.add(page)
    session.commit()
    return page


class TestAuth(FunctionalTests):

    def patch_auth(self, token):
        def _check_token(t):
            if t != token:
                raise ValueError("bad token")
            return AUTH_EMAIL, AUTH_EXPIRES

        return mock.patch("knapsack.views.check_token", _check_token)

    def test_auth(self):
        resp = self.app.get("/pages").json
        self.assertEqual(resp, {u'status': u'error', u'message': u'no auth'})

        with self.patch_auth(AUTH_TOKEN):
            resp = self.app.get("/pages", headers={"AUTH": "XXX"}).json
            self.assertEqual(resp, {u'status': u'error', u'message': u'bad token'})

            resp = self.app.get("/pages", headers={"AUTH": AUTH_TOKEN})

        self.assertEqual(resp.json, {u'status': u'ok', u"pages": []})

        self.assertIn("Set-Cookie", resp.headers)
        set_cookie = resp.headers["Set-Cookie"]

        cookies = SimpleCookie(set_cookie)
        self.assertIn(COOKIE_NAME, cookies)

        cookie = cookies[COOKIE_NAME]

        lockbox = get_lockbox(self.settings)
        token = AuthToken.unserialize(get_auth_secret(lockbox), cookie.value)
        self.assertEqual(token.email, AUTH_EMAIL)
        self.assertEqual(token.expires_in, AUTH_EXPIRES)

        num_users = self.session.query(User).count()
        self.assertEqual(num_users, 1)
        user = self.session.query(User).first()
        self.assertEqual(user.email, AUTH_EMAIL)

    def test_add_page(self):
        URL = "http://example.com/foo.html"
        params = {"page": {"url": URL}}
        with self.patch_auth(AUTH_TOKEN):
            resp = self.app.post_json("/pages", params=params, headers={"AUTH": AUTH_TOKEN})
        rj = resp.json
        self.assertEqual(rj["status"], "ok")
        self.assertEqual(rj["page"]["url"], URL)

    def test_list_pages(self):
        user = create_user(self.session, AUTH_EMAIL)
        with self.patch_auth(AUTH_TOKEN):
            resp = self.app.get("/pages", headers={"AUTH": AUTH_TOKEN})
        self.assertEqual(resp.json, {"status": u"ok", u"pages": []})

        num_users = self.session.query(User).count()
        self.assertEqual(num_users, 1)

        pages = [create_page(self.session, user, "http://example.com/{}.html".format(i)) for i in range(3)]
        with self.patch_auth(AUTH_TOKEN):
            resp = self.app.get("/pages", headers={"AUTH": AUTH_TOKEN})

        rj = resp.json
        self.assertEqual(rj["status"], "ok")
        self.assertEqual(len(rj["pages"]), len(pages))
        for i, page in enumerate(reversed(pages)):
            self.assertEqual(rj["pages"][i]["url"], page.url)
            self.assertEqual(rj["pages"][i]["uid"], page.uid)
            self.assertEqual(rj["pages"][i]["read"], page.read)
            self.assertEqual(rj["pages"][i]["created"], page.created.isoformat())

    def test_delete_page(self):
        URL = "http://example.com/foo.html"
        user = create_user(self.session, AUTH_EMAIL)
        page = create_page(self.session, user, URL)
        self.assertFalse(page.deleted)
        params = {"url": URL}
        with self.patch_auth(AUTH_TOKEN):
            resp = self.app.delete_json("/pages", params=params, headers={"AUTH": AUTH_TOKEN})
        rj = resp.json
        self.assertEqual(rj["status"], "ok")

        self.session.expunge(page)
        page = self.session.query(Page).get(page.id)
        self.assertTrue(page.deleted)

    def test_add_device_token(self):
        TOKEN = "abc123xyz"
        params = {"device_token": TOKEN}
        with self.patch_auth(AUTH_TOKEN):
            resp = self.app.post_json("/device_tokens", params=params, headers={"AUTH": AUTH_TOKEN})
        rj = resp.json
        self.assertEqual(rj["status"], "ok")

        device_token = self.session.query(DeviceToken).first()
        self.assertEqual(device_token.token, TOKEN)
