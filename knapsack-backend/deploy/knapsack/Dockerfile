FROM ubuntu:14.04
MAINTAINER Marc DellaVolpe "marc.dellavolpe@gmail.com"

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update && apt-get install -y python python-dev git python-setuptools libxml2-dev libxslt1-dev zlib1g-dev libpq-dev wamerican
RUN easy_install pip
RUN pip install virtualenv
RUN useradd -ms /bin/bash knapsack

USER knapsack
ENV HOME /home/knapsack/

RUN virtualenv /home/knapsack/env

WORKDIR /home/knapsack/knapsack-backend

CMD ["/bin/bash", "-c", "source ~/env/bin/activate && ~/env/bin/pip install -r requirements.txt && ~/env/bin/python setup.py develop && ~/env/bin/initialize_knapsack-backend_db ~/knapsack-backend/development.ini && pserve --reload development.ini"]
