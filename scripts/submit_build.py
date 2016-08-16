#!/usr/bin/python

"""
Notifies HQ of a new CommCare version. If artifacts are present, the build is
considered a J2ME build.

Requires an ApiUser (corehq.apps.api.models.ApiUser) on the remote_host with
username/password given
"""

import os
import shlex

import subprocess
from subprocess import PIPE
import sys


def submit_build(environ, host):
    target_url = host + "/builds/post/"

    if "ARTIFACTS" in environ:
        raw_command = ('curl -v -H "Expect:" -F "artifacts=@{ARTIFACTS}" ' +
                       '-F "username={USERNAME}" -F "password={PASSWORD}" ' +
                       '-F "build_number={BUILD_NUMBER}" ' +
                       '-F "version={VERSION}" {target_url}')
    else:
        raw_command = ('curl -v -H "Expect:" -F "username={USERNAME}" ' +
                       '-F "build_number={BUILD_NUMBER}" ' +
                       '-F "password={PASSWORD}" ' +
                       '-F "version={VERSION}" {target_url}')
    command = raw_command.format(target_url=target_url, **environ)

    p = subprocess.Popen(shlex.split(command),
                         stdout=PIPE, stderr=None, shell=False)
    return command, p.stdout.read(), ""


if __name__ == "__main__":
    variables = ["USERNAME",
                 "PASSWORD",
                 "ARTIFACTS",
                 "REMOTE_HOST",
                 "VERSION",
                 "BUILD_NUMBER"]
    args = sys.argv[1:]
    environ = {}
    for var in variables:
        if var in os.environ:
            environ[var] = os.environ[var]

    # If no env vars are found, default to reading in arguments passed in
    if len(environ) is 0:
        if len(args) == len(variables):
            environ = dict(zip(variables, args))

    if environ:
        hosts = environ['REMOTE_HOST'].split("+")
        for host in hosts:
            command, out, err = submit_build(environ, host)
            print command
            if out.strip():
                print "--------STDOUT--------"
                print out
            if err.strip():
                print "--------STDERR--------"
                print err
    else:
        print("submit_build.py <%s>" % ("> <".join(variables)))
