import os

from setuptools import setup, find_packages

here = os.path.abspath(os.path.dirname(__file__))


setup(name='knapsack',
      packages=find_packages(),
      include_package_data=True,
      zip_safe=False,
      test_suite='knapsack',
      entry_points="""\
      [paste.app_factory]
      main = knapsack:main
      [console_scripts]
      initialize_knapsack_db = knapsack.scripts.initializedb:main
      """,
      )
