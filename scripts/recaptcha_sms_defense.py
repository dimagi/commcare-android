#!/usr/bin/env python3

"""
Toggles reCAPTCHA SMS Defense (toll fraud protection) for Firebase Auth phone
sign-in, per CCCT-2723.

    ./scripts/recaptcha_sms_defense.py status --project PROJECT_ID
    ./scripts/recaptcha_sms_defense.py on --mode audit --project PROJECT_ID
    ./scripts/recaptcha_sms_defense.py on --mode enforce --project PROJECT_ID
    ./scripts/recaptcha_sms_defense.py off --project PROJECT_ID

Every mutation prints a diff and asks for confirmation, and writes the
pre-change config to a timestamped backup file. Requires roles/firebaseauth.admin
on the project.

One-time gcloud setup, needed because the access token is fetched at runtime:

    1. brew install --cask google-cloud-sdk
    2. gcloud auth login
    3. gcloud config set project <firebase_project_id>

Where to check current state:

    - `status` above is authoritative: it reads phoneEnforcementState,
      useSmsTollFraudProtection and the threshold rules straight from the API.
    - Google Cloud console, console.cloud.google.com/security/recaptcha ->
      Settings -> SMS defense pane -> Configure, shows only an Enable toggle.
      It does NOT show the enforcement mode or the threshold, so it cannot
      distinguish AUDIT from ENFORCE. The Firebase console has no equivalent page.
    - Scores and error rates during a rollout live in Cloud Monitoring, under
      identitytoolkit.googleapis.com/recaptcha/{verdict_count,token_count,
      sms_tf_risk_scores}. There is no console dashboard for risk scores.

If this script cannot run, the equivalent rollback is:

    curl -s -X PATCH -H "Authorization: Bearer $(gcloud auth print-access-token)" \
      -H "Content-Type: application/json" \
      "https://identitytoolkit.googleapis.com/admin/v2/projects/<firebase_project_id>/config?updateMask=recaptchaConfig.phoneEnforcementState,recaptchaConfig.useSmsTollFraudProtection,recaptchaConfig.tollFraudManagedRules" \
      -d '{"recaptchaConfig":{"phoneEnforcementState":"OFF","useSmsTollFraudProtection":false,"tollFraudManagedRules":[]}}'
"""

import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

API_ROOT = "https://identitytoolkit.googleapis.com/admin/v2"

FIELDS = (
    "recaptchaConfig.phoneEnforcementState",
    "recaptchaConfig.useSmsTollFraudProtection",
    "recaptchaConfig.tollFraudManagedRules",
)


class ApiError(Exception):
    pass


def access_token():
    try:
        result = subprocess.run(
            ["gcloud", "auth", "print-access-token"],
            capture_output=True, text=True, check=True,
        )
    except FileNotFoundError:
        raise ApiError(
            "gcloud not found on PATH; see the setup steps at the top of this file"
        )
    except subprocess.CalledProcessError as exc:
        raise ApiError("gcloud auth failed: %s" % exc.stderr.strip())
    return result.stdout.strip()


def request(project, token, method, query=None, body=None):
    url = "%s/projects/%s/config" % (API_ROOT, project)
    if query:
        url += "?" + query
    payload = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=payload, method=method)
    req.add_header("Authorization", "Bearer %s" % token)
    req.add_header("x-goog-user-project", project)
    if payload is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as exc:
        raise ApiError("HTTP %s\n%s" % (exc.code, exc.read().decode(errors="replace")))
    except urllib.error.URLError as exc:
        raise ApiError("network error: %s" % exc.reason)


def get_config(project, token):
    return request(project, token, "GET")


def relevant(config):
    """Extracts the three fields this script owns, for display and diffing.

    The defaults below stand in for keys the API omits entirely when a feature has
    never been configured. An `[]` or `false` in the output can therefore mean
    "absent server-side" rather than "explicitly set to empty".
    """
    recaptcha = config.get("recaptchaConfig", {})
    return {
        "phoneEnforcementState": recaptcha.get("phoneEnforcementState", "OFF"),
        "useSmsTollFraudProtection": recaptcha.get("useSmsTollFraudProtection", False),
        "tollFraudManagedRules": recaptcha.get("tollFraudManagedRules", []),
    }


def target_state(action, mode, threshold):
    """Builds the target recaptchaConfig for `on` or `off`.

    There is no standalone threshold field in the API. The threshold lives as
    `startScore` inside a `tollFraudManagedRules` entry, meaning "start blocking at
    this fraud-likelihood score" — so a higher value is more permissive, the inverse
    of reCAPTCHA v3 site scores. Bot protection uses a separate `managedRules` array
    keyed on `endScore`, which runs the other way; this script never touches it.

    `off` removes the rule rather than setting startScore to 0. A stored 0 would mean
    "block everything from score 0 upward", so leaving the key absent means a stray
    enforcement flip has no threshold to act on.
    """
    if action == "off":
        return {
            "phoneEnforcementState": "OFF",
            "useSmsTollFraudProtection": False,
            "tollFraudManagedRules": [],
        }
    return {
        "phoneEnforcementState": mode.upper(),
        "useSmsTollFraudProtection": True,
        "tollFraudManagedRules": [{"action": "BLOCK", "startScore": threshold}],
    }


def render(state):
    return json.dumps(state, indent=2, sort_keys=True)


def print_diff(current, target):
    print("current:")
    print(render(current))
    print("\ntarget:")
    print(render(target))


def warn_unrelated(config):
    recaptcha = config.get("recaptchaConfig", {})
    warnings = []
    if recaptcha.get("useSmsBotScore"):
        warnings.append(
            "useSmsBotScore is true: reCAPTCHA bot protection shares "
            "phoneEnforcementState and will change behaviour alongside SMS Defense"
        )
    if not recaptcha.get("recaptchaKeys"):
        warnings.append("no recaptchaKeys configured on this project")
    for warning in warnings:
        print("WARNING: %s" % warning, file=sys.stderr)


def backup(project, config):
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    path = Path.home() / ("recaptcha-config-%s-%s.json" % (project, stamp))
    path.write_text(json.dumps(config, indent=2, sort_keys=True))
    return path


def confirm(action, project):
    prompt = "Apply %s to project %s? [yes/N] " % (action, project)
    return input(prompt).strip().lower() == "yes"


def main():
    parser = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    parser.add_argument("action", choices=["status", "on", "off"])
    parser.add_argument("--project", required=True)
    parser.add_argument("--mode", choices=["audit", "enforce"], default="audit")
    parser.add_argument("--threshold", type=float, default=0.8)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--yes", action="store_true")
    args = parser.parse_args()

    if args.action == "on" and not 0.0 <= args.threshold <= 0.9:
        parser.error("--threshold must be between 0.0 and 0.9")

    try:
        token = access_token()
        config = get_config(args.project, token)
        current = relevant(config)

        if args.action == "status":
            print(render(current))
            warn_unrelated(config)
            return 0

        target = target_state(args.action, args.mode, args.threshold)
        print_diff(current, target)
        warn_unrelated(config)

        if current == target:
            print("\nalready in the target state, nothing to do")
            return 0

        if args.dry_run:
            print("\nPATCH ?updateMask=%s" % ",".join(FIELDS))
            print(render({"recaptchaConfig": target}))
            return 0

        if not args.yes and not confirm(args.action, args.project):
            print("aborted")
            return 1

        print("\nbacked up current config to %s" % backup(args.project, config))
        updated = request(
            args.project, token, "PATCH",
            query="updateMask=" + ",".join(FIELDS),
            body={"recaptchaConfig": target},
        )
        applied = relevant(updated)
        print("\napplied:")
        print(render(applied))
        if applied != target:
            print("\nWARNING: server state differs from target", file=sys.stderr)
            return 1
    except ApiError as exc:
        print("error: %s" % exc, file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130
    return 0


if __name__ == "__main__":
    sys.exit(main())
