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

def buildTestCommand(appToken, testToken, classes=None):
    test = {}
    if "BROWSERSTACK_DEVICES" in os.environ:
        list = os.environ["BROWSERSTACK_DEVICES"]
        devices = [x.strip() for x in list.split(',')]
    else:
        devices = ["LG G5-6.0", "Google Nexus 5-4.4"]
    test["devices"] = devices
    test["project"] = "CommCare"
    test["app"] = appToken
    test["deviceLogs"] = True
    test["testSuite"] = testToken
    test["networkLogs"] = True
    test["shards"] = {
        "numberOfShards": 5
    }
    test["annotation"] = ["org.commcare.annotations.BrowserstackTests"]

    if classes:
        test["class"] = classes

    return json.dumps(json.dumps(test))

def isSuccessfulBuild(buildId):
    resultCommand = 'curl -u "{}:{}" -X GET "https://api-cloud.browserstack.com/app-automate/espresso/v2/builds/{}"'.format(userName, password, buildId)
    result = subprocess.Popen(shlex.split(resultCommand), stdout=PIPE, stderr=None, shell=False)
    status = json.loads(result.communicate()[0])["status"]

    while status == "running":
        print("Waiting for tests on browserstack to complete")
        time.sleep(120)
        result = subprocess.Popen(shlex.split(resultCommand), stdout=PIPE, stderr=None, shell=False)
        status = json.loads(result.communicate()[0])["status"]

    return status


def testResult(buildId):
    status = isSuccessfulBuild(buildId)

    # if test succeeded then we can simply return from here.
    if (status == "passed"):
        return

    # Otherwise run the failing test one more time.

    # Get the sessionID from test result
    resultCommand = 'curl -u "{}:{}" -X GET "https://api-cloud.browserstack.com/app-automate/espresso/v2/builds/{}"'.format(userName, password, buildId)
    result = subprocess.Popen(shlex.split(resultCommand), stdout=PIPE, stderr=None, shell=False)
    sessions = json.loads(result.communicate()[0])["devices"][0]["sessions"]

    # Loop over all the sessions and Create an array of failing classes.

    classes = []
    for session in sessions:
        if (session["status"] == "passed"):
            continue

        sessionId = session["id"]

        # Gather the sessionDetails
        testDetailsCommand = 'curl -u "{}:{}" -X GET "https://api-cloud.browserstack.com/app-automate/espresso/v2/builds/{}/sessions/{}"'.format(userName, password, buildId, sessionId)
        testDetailsResult = subprocess.Popen(shlex.split(testDetailsCommand), stdout=PIPE, stderr=None, shell=False)
        testcases = json.loads(testDetailsResult.communicate()[0])["testcases"]["data"]

        # Collect the failed classes
        for testcase in testcases:
            methods = testcase["testcases"]
            for method in methods:
                if (method["status"] != "passed"):
                    failedClassName = "org.commcare.androidTests." + testcase["class"]
                    if (failedClassName not in classes):
                        classes.append(failedClassName)
                    break

    # Now that we know all the test-classes that are failing, we can re-run those.
    print("Failing test classes :: ", classes)

    runConfig = buildTestCommand(appToken, testToken, classes)
    runCmd = 'curl -X POST "{}" -d \ {} -H "Content-Type: application/json" -u "{}:{}"'.format(espressoUrl, runConfig, userName, password)
    output = subprocess.Popen(shlex.split(runCmd), stdout=PIPE, stderr=None, shell=False).communicate()
    buildId = json.loads(output[0])["build_id"]

    status = isSuccessfulBuild(buildId)

    if (status != "passed"):
        print("Instrumentation Tests Failed. Visit browserstack dashboard for more details.")
        print("https://app-automate.browserstack.com/dashboard/v2/builds/{}".format(buildId))
        sys.exit(-1)


if __name__ == "__main__":

    if "BROWSERSTACK_USERNAME" in os.environ:
        userName = os.environ["BROWSERSTACK_USERNAME"]

    if "BROWSERSTACK_PASSWORD" in os.environ:
        password = os.environ["BROWSERSTACK_PASSWORD"]

    releaseApp = os.environ["RELEASE_APP_LOCATION"]
    testApk = os.environ["TEST_APP_LOCATION"]

    releaseUrl = "https://api-cloud.browserstack.com/app-automate/upload"
    testUrl = "https://api-cloud.browserstack.com/app-automate/espresso/test-suite"

    command = 'curl -u "{}:{}" -X POST "{}" -F'

    releaseUploadCmd = appendData(command.format(userName, password, releaseUrl), releaseApp)
    output = subprocess.Popen(shlex.split(releaseUploadCmd), stdout=PIPE, stderr=None, shell=False)
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
    testResult(buildId)
