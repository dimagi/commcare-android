"""
Submits the apks to BrowserStack to run the instrumentation tests.
"""

import os
import shlex
import subprocess
from subprocess import PIPE
import sys
import json
import time

def appendData(command, dataUrl):
    var = "file=@{}".format(dataUrl)
    return command + " " + json.dumps(var)

def buildTestCommand(appToken, testToken):
    test = {}
    if "BROWSERSTACK_DEVICES" in os.environ:
        list = os.environ["BROWSERSTACK_DEVICES"]
        devices = [x.strip() for x in list.split(',')]
    else:
        devices = ["LG G5-6.0", "Google Nexus 5-4.4"]
    test["devices"] = devices
    test["app"] = appToken
    test["deviceLogs"] = True
    test["testSuite"] = testToken
    return json.dumps(json.dumps(test))

if __name__ == "__main__":

    if "BROWSERSTACK_USERNAME" in os.environ:
        userName = os.environ["BROWSERSTACK_USERNAME"]

    if "BROWSERSTACK_PASSWORD" in os.environ:
        password = os.environ["BROWSERSTACK_PASSWORD"]

    if "BASE_LOCATION" in os.environ:
        baseLoc = os.environ["BASE_LOCATION"]

    debugAppBundle = baseLoc + "bundle/commcareDebug/app-commcare-debug.aab"
    testApk = baseLoc + "apk/androidTest/commcare/debug/app-commcare-debug-androidTest.apk"

    debugUrl = "https://api-cloud.browserstack.com/app-automate/upload"
    testUrl = "https://api-cloud.browserstack.com/app-automate/espresso/test-suite"

    command = 'curl -u "{}:{}" -X POST "{}" -F'

    debugUploadCmd = appendData(command.format(userName, password, debugUrl), debugAppBundle)
    output = subprocess.Popen(shlex.split(debugUploadCmd), stdout=PIPE, stderr=None, shell=False)
    appToken = json.loads(output.communicate()[0])["app_url"]

    testUploadCmd = appendData(command.format(userName, password, testUrl), testApk)
    output = subprocess.Popen(shlex.split(testUploadCmd), stdout=PIPE, stderr=None, shell=False)
    testToken = json.loads(output.communicate()[0])["test_url"]

    # Running the tests

    espressoUrl = "https://api-cloud.browserstack.com/app-automate/espresso/build"
    runConfig = buildTestCommand(appToken, testToken)
    runCmd = 'curl -X POST "{}" -d \ {} -H "Content-Type: application/json" -u "{}:{}"'.format(espressoUrl, runConfig, userName, password)

    output = subprocess.Popen(shlex.split(runCmd), stdout=PIPE, stderr=None, shell=False).communicate()
    print(output)

    buildId = json.loads(output[0])["build_id"]

    # Get the result of the test build

    resultCommand = 'curl -u "{}:{}" -X GET "https://api-cloud.browserstack.com/app-automate/espresso/builds/{}"'.format(userName, password, buildId)

    result = subprocess.Popen(shlex.split(resultCommand), stdout=PIPE, stderr=None, shell=False)
    status = json.loads(result.communicate()[0])["status"]

    while status == "running":
        print("Waiting for tests on browserstack to complete")
        time.sleep(60)
        result = subprocess.Popen(shlex.split(resultCommand), stdout=PIPE, stderr=None, shell=False)
        status = json.loads(result.communicate()[0])["status"]

    if (status == "failed" or status == "error"):
        print("Instrumentation Tests Failed. Visit browserstack dashboard for more details.")
        sys.exit(-1)
