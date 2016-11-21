
FROM ubuntu
MAINTAINER Marc DellaVolpe "marc.dellavolpe@gmail.com"

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update
RUN apt-get install -y python python-dev python-setuptools python-pip uwsgi uwsgi-plugin-python libpq-dev

RUN mkdir /site
RUN mkdir /site/knapsack-backend
COPY . /site/knapsack-backend
RUN cd /site/knapsack-backend && python /site/knapsack-backend/setup.py install

RUN useradd -ms /bin/bash knapsack

USER knapsack
ENV HOME /site/knapsack-backend
ENV PYTHON_EGG_CACHE /tmp/python-eggs
WORKDIR /site/knapsack-backend

CMD ["uwsgi", "--plugin", "python", "--paste", "config:/site/knapsack-backend/production.ini" , "-s", "0.0.0.0:3100", "--master", "--workers", "3"]
