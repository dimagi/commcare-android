"""
Submits the apks to BrowserStack to run the instrumentation tests.
"""

import os
import shlex
import subprocess
from subprocess import PIPE
import sys
import json

def appendData(command, dataUrl):
    var = 'data={\"url\": \"%s\"}' % dataUrl
    return command + " " + json.dumps(var)

def buildTestCommand(appToken, testToken):
    test = {}
    test["devices"] = ["LG G5-6.0"] #using just 1 device right now.
    test["app"] = appToken
    test["deviceLogs"] = True
    test["testSuite"] = testToken
    return json.dumps(json.dumps(test))

if __name__ == "__main__":

    if "BROWSERSTACK_USERNAME" in os.environ:
        userName = os.environ["BROWSERSTACK_USERNAME"]

    if "BROWSERSTACK_PASSWORD" in os.environ:
        password = os.environ["BROWSERSTACK_PASSWORD"]

    if "BUILD_NUMBER" in os.environ:
        buildNumber = os.environ["BUILD_NUMBER"]

    base = "https://jenkins.dimagi.com/job/commcare-android-instrumentation-tests/{}".format(buildNumber)
    debugApk = base + "/artifact/app-commcare-debug.apk"
    testApk = base + "/artifact/app-commcare-debug-androidTest.apk"

    debugUrl = "https://api-cloud.browserstack.com/app-automate/upload"
    testUrl = "https://api-cloud.browserstack.com/app-automate/espresso/test-suite"

    command = 'curl -u "{}:{}" -X POST "{}" -F'

    debugUploadCmd = appendData(command.format(userName, password, debugUrl), debugApk)
    output = subprocess.Popen(shlex.split(debugUploadCmd), stdout=PIPE, stderr=None, shell=False)
    appToken = json.loads(output.communicate()[0])["app_url"]

    testUploadCmd = appendData(command.format(userName, password, testUrl), testApk)
    output = subprocess.Popen(shlex.split(testUploadCmd), stdout=PIPE, stderr=None, shell=False)
    testToken = json.loads(output.communicate()[0])["test_url"]

    # Running the tests on LG-G5

    espressoUrl = "https://api-cloud.browserstack.com/app-automate/espresso/build"
    runConfig = buildTestCommand(appToken, testToken)
    runCmd = 'curl -X POST "{}" -d \ {} -H "Content-Type: application/json" -u "{}:{}"'.format(espressoUrl, runConfig, userName, password)

    output = subprocess.Popen(shlex.split(runCmd), stdout=PIPE, stderr=None, shell=False)
    print(output.communicate())
