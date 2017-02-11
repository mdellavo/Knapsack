import mock

import unittest

from pyramid.paster import get_appsettings
from webtest import TestApp as WebTestApp

from knapsack import main
from knapsack.models import Base, Session


class FunctionalTests(unittest.TestCase):
    def setUp(self):
        settings = get_appsettings("development.ini#knapsack")
        settings['sqlalchemy.url'] = 'sqlite:///:memory:'

        app = main({}, **settings)
        self.app = WebTestApp(app)

        self.engine = Session.session_factory.kw['bind']
        Base.metadata.drop_all(bind=self.engine)
        Base.metadata.create_all(bind=self.engine)


class TestAuth(FunctionalTests):

    def patch_auth(self, token):
        def _check_token(t):
            if t != token:
                raise ValueError("bad token")
            return "foo@example.com", 600

        return mock.patch("knapsack.views.check_token", _check_token)

    def test_auth(self):
        resp = self.app.get("/pages").json
        self.assertEqual(resp, {u'status': u'error', u'message': u'no auth'})

        TOKEN = "foo-bar-baz"
        with self.patch_auth(TOKEN):
            resp = self.app.get("/pages", headers={"AUTH": "XXX"}).json
            self.assertEqual(resp, {u'status': u'error', u'message': u'bad token'})

            resp = self.app.get("/pages", headers={"AUTH": TOKEN}).json
            self.assertEqual(resp, {u'status': u'ok'})

