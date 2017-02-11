import datetime
import uuid

from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Boolean
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import scoped_session, sessionmaker, relationship
from zope.sqlalchemy import ZopeTransactionExtension


Session = scoped_session(sessionmaker(expire_on_commit=False, extension=ZopeTransactionExtension()))
Base = declarative_base()

DATETIME_FORMAT = '%Y-%m-%dT%H:%M:%S.%f'


class DeviceToken(Base):
    __tablename__ = 'device_tokens'

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey('users.id'), nullable=False)
    created_on = Column(DateTime, nullable=False, default=datetime.datetime.utcnow)
    token = Column(String, nullable=False)
    active = Column(Boolean, nullable=False, default=True)
    deactivated_on = Column(DateTime)
    model = Column(String)


class Page(Base):
    __tablename__ = 'pages'

    id = Column(Integer, primary_key=True)
    uid = Column(String, unique=True, nullable=False, default=lambda: uuid.uuid4().hex)
    user_id = Column(Integer, ForeignKey('users.id'), nullable=False)
    url = Column(String)
    title = Column(String)
    created = Column(DateTime, nullable=False, default=datetime.datetime.utcnow)
    read = Column(Boolean, default=False)
    deleted = Column(Boolean, default=False)

    def to_dict(self):
        return {
            'uid': self.uid,
            'url': self.url,
            'title': self.title,
            'created': self.created.strftime(DATETIME_FORMAT),
            'read': self.read
        }


class User(Base):
    __tablename__ = 'users'

    id = Column(Integer, primary_key=True)
    email = Column(String, nullable=False, unique=True)
    created_on = Column(DateTime, nullable=False, default=datetime.datetime.utcnow)

    pages = relationship(Page, lazy='dynamic', backref='user', order_by=Page.created.desc())
    device_tokens = relationship(DeviceToken, lazy='dynamic', backref='user')

    @property
    def active_pages(self):
        return self.pages.filter(Page.deleted == False)

    @property
    def active_device_tokens(self):
        return [token for token in self.device_tokens if token.active]