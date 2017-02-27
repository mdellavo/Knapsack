
FROM ubuntu
MAINTAINER Marc DellaVolpe "marc.dellavolpe@gmail.com"

ENV DEBIAN_FRONTEND noninteractive

RUN useradd -ms /bin/bash knapsack

RUN apt-get update
RUN apt-get install -y python python-dev python-setuptools python-pip uwsgi uwsgi-plugin-python libpq-dev git

COPY requirements.txt /tmp
RUN pip install -r /tmp/requirements.txt
RUN rm /tmp/requirements.txt

RUN apt-get purge -y git
RUN apt-get autoremove -y

RUN mkdir /tmp/knapsack
COPY . /tmp/knapsack

RUN cd /tmp/knapsack/ && python setup.py install

RUN mkdir /site
COPY production.ini /site/knapsack.ini
COPY knapsack.lockbox /site

USER knapsack
ENV HOME /site
ENV PYTHON_EGG_CACHE /tmp/python-eggs
WORKDIR /site

CMD ["uwsgi", "--plugin", "python", "--paste", "config:/site/knapsack.ini" , "-s", "0.0.0.0:3100", "--master", "--workers", "3"]
