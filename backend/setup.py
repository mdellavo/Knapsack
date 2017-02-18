import os

from setuptools import setup, find_packages

here = os.path.abspath(os.path.dirname(__file__))

with open(os.path.join(here, 'requirements.txt')) as f:
    requires = [l.strip() for l in f]

setup(name='knapsack',
      packages=find_packages(),
      include_package_data=True,
      zip_safe=False,
      test_suite='knapsack',
      install_requires=requires,
      entry_points="""\
      [paste.app_factory]
      main = knapsack:main
      [console_scripts]
      initialize_knapsack_db = knapsack.scripts.initializedb:main
      """,
      )
