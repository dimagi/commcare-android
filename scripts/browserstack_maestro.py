"""
Submits the app APK and Maestro flows to BrowserStack App Automate.
"""

import json
import os
import shlex
import subprocess
import sys
import tempfile
import time
import zipfile
from subprocess import PIPE


def zip_flows_dir(flows_dir):
    """Zips all .yaml files in the flows directory (including subdirectories)."""
    tmp = tempfile.NamedTemporaryFile(suffix=".zip", delete=False)
    with zipfile.ZipFile(tmp.name, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, _, files in os.walk(flows_dir):
            for file in files:
                if file.endswith(".yaml") or file.endswith(".yml"):
                    full_path = os.path.join(root, file)
                    arcname = os.path.relpath(full_path, flows_dir)
                    zf.write(full_path, arcname)
    return tmp.name


def build_test_command(app_token, test_token):
    if "BROWSERSTACK_DEVICES" in os.environ:
        devices = [x.strip() for x in os.environ["BROWSERSTACK_DEVICES"].split(",")]
    else:
        devices = ["Google Pixel 3-9.0", "Samsung Galaxy S21-11.0"]

    test = {
        "app": app_token,
        "testSuite": test_token,
        "devices": devices,
        "project": "CommCare",
        "deviceLogs": True,
        "networkLogs": True,
    }
    return json.dumps(json.dumps(test))


def is_successful_build(build_id):
    result_cmd = (
        'curl -u "{}:{}" -X GET "{}/{}"'.format(
            user_name, password, maestro_builds_url, build_id
        )
    )
    result = subprocess.Popen(shlex.split(result_cmd), stdout=PIPE, stderr=None, shell=False)
    status = json.loads(result.communicate()[0])["status"]

    while status == "running":
        print("Waiting for Maestro tests on BrowserStack to complete...")
        time.sleep(60)
        result = subprocess.Popen(shlex.split(result_cmd), stdout=PIPE, stderr=None, shell=False)
        status = json.loads(result.communicate()[0])["status"]

    return status


def get_failed_flows(build_id):
    """Returns a list of flow file names that failed in the given build."""
    result_cmd = (
        'curl -u "{}:{}" -X GET "{}/{}"'.format(
            user_name, password, maestro_builds_url, build_id
        )
    )
    result = subprocess.Popen(shlex.split(result_cmd), stdout=PIPE, stderr=None, shell=False)
    json_result = json.loads(result.communicate()[0])
    print(json_result)

    failed_flows = []
    for device in json_result.get("devices", []):
        for session in device.get("sessions", []):
            if session.get("status") != "passed":
                flow_name = session.get("flow_name") or session.get("name")
                if flow_name and flow_name not in failed_flows:
                    failed_flows.append(flow_name)

    return failed_flows


def test_result(app_token, test_token, build_id, retry_count):
    status = is_successful_build(build_id)

    if status == "passed":
        print("Maestro Tests Passed.")
        return

    retry_count += 1
    failed_flows = get_failed_flows(build_id)
    print("Failing flows :: ", failed_flows)

    if not failed_flows:
        print("Could not determine failing flows - re-running full suite.")
        new_test_token = test_token
    else:
        # Zip only the failing flows for the retry
        flows_dir = os.environ.get("MAESTRO_FLOWS_DIR", "maestro")
        tmp_zip = tempfile.NamedTemporaryFile(suffix=".zip", delete=False)
        with zipfile.ZipFile(tmp_zip.name, "w", zipfile.ZIP_DEFLATED) as zf:
            for flow_name in failed_flows:
                full_path = os.path.join(flows_dir, flow_name)
                if os.path.exists(full_path):
                    zf.write(full_path, flow_name)
                    # Include any subflows it might reference
                    flows_subdir = os.path.join(flows_dir, "flows")
                    if os.path.isdir(flows_subdir):
                        for root, _, files in os.walk(flows_subdir):
                            for f in files:
                                if f.endswith(".yaml") or f.endswith(".yml"):
                                    p = os.path.join(root, f)
                                    zf.write(p, os.path.relpath(p, flows_dir))

        upload_cmd = 'curl -u "{}:{}" -X POST "{}" -F "file=@{}"'.format(
            user_name, password, maestro_test_suite_url, tmp_zip.name
        )
        output = subprocess.Popen(shlex.split(upload_cmd), stdout=PIPE, stderr=None, shell=False)
        new_test_token = json.loads(output.communicate()[0])["test_suite_url"]

    run_config = build_test_command(app_token, new_test_token)
    run_cmd = 'curl -X POST "{}" -d \\ {} -H "Content-Type: application/json" -u "{}:{}"'.format(
        maestro_build_url, run_config, user_name, password
    )
    output = subprocess.Popen(shlex.split(run_cmd), stdout=PIPE, stderr=None, shell=False).communicate()
    json_result = json.loads(output[0])
    print(json_result)
    new_build_id = json_result["build_id"]

    status = is_successful_build(new_build_id)

    if status != "passed" and retry_count >= 3:
        print("Maestro Tests Failed. Visit BrowserStack dashboard for details.")
        print("https://app-automate.browserstack.com/dashboard/v2/builds/{}".format(new_build_id))
        sys.exit(-1)
    elif status != "passed":
        test_result(app_token, new_test_token, new_build_id, retry_count)
    else:
        print("Maestro Tests Passed.")


def should_skip_android_test():
    git_pr_id = os.environ.get("PR_NUMBER", "")
    if not git_pr_id:
        return False
    git_pr_cmd = 'curl -H "Accept: application/vnd.github.v3+json" https://api.github.com/repos/dimagi/commcare-android/pulls/{}'.format(git_pr_id)
    output = subprocess.Popen(shlex.split(git_pr_cmd), stdout=PIPE, stderr=None, shell=False).communicate()
    labels = json.loads(output[0])["labels"]
    return any(label["name"] == "skip-integration-tests" for label in labels)


def run_maestro_test():
    if should_skip_android_test():
        return

    upload_cmd = 'curl -u "{}:{}" -X POST "{}" -F'.format(user_name, password, app_upload_url)

    # Upload the app APK
    app_upload = upload_cmd + ' "file=@{}"'.format(release_app)
    output = subprocess.Popen(shlex.split(app_upload), stdout=PIPE, stderr=None, shell=False)
    app_upload_response = json.loads(output.communicate()[0])
    app_token = app_upload_response["app_url"]

    # Zip and upload the Maestro flows
    flows_dir = os.environ.get("MAESTRO_FLOWS_DIR", "maestro")
    zip_path = zip_flows_dir(flows_dir)
    test_upload = 'curl -u "{}:{}" -X POST "{}" -F "file=@{}"'.format(
        user_name, password, maestro_test_suite_url, zip_path
    )
    output = subprocess.Popen(shlex.split(test_upload), stdout=PIPE, stderr=None, shell=False)
    test_upload_response = json.loads(output.communicate()[0])
    test_token = test_upload_response["test_suite_url"]

    # Execute
    run_config = build_test_command(app_token, test_token)
    run_cmd = 'curl -X POST "{}" -d \\ {} -H "Content-Type: application/json" -u "{}:{}"'.format(
        maestro_build_url, run_config, user_name, password
    )
    output = subprocess.Popen(shlex.split(run_cmd), stdout=PIPE, stderr=None, shell=False).communicate()
    print(output)

    build_id = json.loads(output[0])["build_id"]
    test_result(app_token, test_token, build_id, 1)


if __name__ == "__main__":
    user_name = os.environ["BROWSERSTACK_USERNAME"]
    password = os.environ["BROWSERSTACK_PASSWORD"]
    release_app = os.environ["RELEASE_APP_LOCATION"]

    app_upload_url = "https://api-cloud.browserstack.com/app-automate/maestro/v2/app"
    maestro_test_suite_url = "https://api-cloud.browserstack.com/app-automate/maestro/v2/test-suite"
    maestro_build_url = "https://api-cloud.browserstack.com/app-automate/maestro/v2/android/build"
    maestro_builds_url = "https://api-cloud.browserstack.com/app-automate/maestro/v2/builds"

    run_maestro_test()
