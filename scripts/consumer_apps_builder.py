
import os
import shutil
import subprocess 
import sys
import xml.etree.ElementTree as ET

# Script to build the .apks for all consumer apps, off of the latest release build of CommCare on jenkins
# All paths are written assuming this script will be run from the PARENT directory of commcare-odk/,
# unless otherwise specified 

# Path to the directory where all user-provided information and files for each consumer app lives. 
# This directory should store a list of directories, 1 for each consumer app we are building.
# The expected format for a single app directory is as follows:
# - config.txt: a text file containing the following 5 pieces of information in order, on a single comma-separated line, e.g: app_id,app_domain,build_number,username,password
# - ic_launcher.zip: a zip file generated from Android Asset Studio of the desired app icon (MUST have this exact name)
PATH_TO_STATIC_RESOURCES_DIR = "./consumer-apps-resources"
CONFIG_FILE_NAME = "config.txt"
ZIP_FILE_NAME = "ic_launcher.zip"

# Path to the commcare-odk app directory
PATH_TO_ANDROID_DIR = "./commcare-android/"

# Path to the standalone directory
PATH_TO_STANDALONE_DIR = PATH_TO_ANDROID_DIR + "app/standalone/"

# Path to the directory where all app assets should be placed, RELATIVE to the commcare-odk/
# directory, since we have cd'ed into that directory at the time this is used
PATH_TO_ASSETS_DIR_FROM_ODK = "./app/standalone/assets/"


def checkout_or_update_static_resources_repo():
    if not os.path.exists(PATH_TO_STATIC_RESOURCES_DIR):
        subprocess.call(["git", "clone", "https://github.com/dimagi/consumer-apps-resources"])
    os.chdir(PATH_TO_STATIC_RESOURCES_DIR)
    subprocess.call(["git", "checkout", "master"])
    subprocess.call(["git", "pull", "origin", "master"])
    os.chdir('../')


def build_apks_from_resources(build_type, subset_of_apps_to_build):
    for (app_dir_name, sub_dir_list, files_list) in os.walk(PATH_TO_STATIC_RESOURCES_DIR):
        if '.git' not in app_dir_name and app_dir_name != PATH_TO_STATIC_RESOURCES_DIR:
            if subset_of_apps_to_build is None or in_list(subset_of_apps_to_build, app_dir_name):
                build_apk_from_directory_contents(app_dir_name, files_list, build_type)


def in_list(app_list, app_dir_path):
    plain_app_name = app_dir_path[(app_dir_path.rfind('/') + 1):]
    return plain_app_name in app_list


def build_apk_from_directory_contents(app_sub_dir, files_list, build_type):
    if CONFIG_FILE_NAME not in files_list:
        raise Exception("One of the app resource directories does not contain the required config.txt file.")
    if ZIP_FILE_NAME not in files_list:
        raise Exception("One of the app resource directories does not contain the required ic_launcher.zip file.")

    full_path_to_config_file = os.path.join(app_sub_dir, CONFIG_FILE_NAME)
    full_path_to_zipfile = os.path.join(app_sub_dir, ZIP_FILE_NAME)

    unzip_app_icon(full_path_to_zipfile)
    app_id, domain, build_number, username, password = get_app_fields(full_path_to_config_file)
    
    os.chdir(PATH_TO_ANDROID_DIR)
    download_ccz(app_id, domain, build_number)
    download_restore_file(domain, username, password)
    assemble_apk(domain, build_number, username, password, build_type)
    move_apk(app_id, build_type)
    os.chdir('../')


def unzip_app_icon(zipfile_name):
    # -o option overwrites the existing files without prompting for confirmation
    subprocess.call(["unzip", "-o", zipfile_name, "-d", PATH_TO_STANDALONE_DIR])


def get_app_fields(config_filename):
    f = open(config_filename, 'r')
    line = f.readline().strip('\n')
    return tuple(line.split(","))


def download_ccz(app_id, domain, build_number):
    subprocess.call(["./scripts/download_app_into_standalone_asset.sh", domain, app_id, PATH_TO_ASSETS_DIR_FROM_ODK, build_number])


def download_restore_file(domain, username, password):
    subprocess.call(["./scripts/download_restore_into_standalone_asset.sh", domain, username, password, PATH_TO_ASSETS_DIR_FROM_ODK])


def assemble_apk(domain, build_number, username, password, build_type):
    if build_type == 'd':
        gradle_directive = "assembleStandaloneDebug"
    else:
        gradle_directive = "assembleStandaloneRelease"
    subprocess.call(["gradle", gradle_directive, 
        "-Pcc_domain={}".format(domain), 
        "-Papplication_name={}".format(get_app_name_from_profile()), 
        "-Pis_consumer_app=true", 
        "-Prun_download_scripts=false",
        "-PversionCode={}".format(build_number),
        "-Pusername={}".format(username),
        "-Ppassword={}".format(password)])


def get_app_name_from_profile():
    tree = ET.parse(PATH_TO_ASSETS_DIR_FROM_ODK + '/direct_install/profile.ccpr')
    return escape_apostrophes(tree.getroot().get("name").encode('utf-8'))


# Necessary because down the line, application_name ends up in the values.xml file, where any
# quotes need to be escaped
def escape_apostrophes(s):
    return s.replace("'", "\\'")


def move_apk(app_id, build_type):
    CONSUMER_APKS_DIR = "./build/outputs/consumer_apks"
    if not os.path.exists(CONSUMER_APKS_DIR):
        os.mkdir(CONSUMER_APKS_DIR) 
    if build_type == 'd':
        original_apk_filename = "./build/outputs/apk/commcare-android-standalone-debug.apk"
    else:
        original_apk_filename = "./build/outputs/apk/commcare-android-standalone-release.apk"
    shutil.move(original_apk_filename, os.path.join(CONSUMER_APKS_DIR, "{}.apk".format(app_id)))


def main():
    if len(sys.argv) < 2:
        raise Exception("Must specify a build type. Use 'd' for debug or 'r' for release.")

    build_type = sys.argv[1]
    if build_type != 'd' and build_type != 'r':
        raise Exception("Must specify a build type. Use 'd' for debug or 'r' for release.")

    if len(sys.argv) > 2:
        # For debugging purposes- Allows the user to pass in a comma-separated list of app
        # directory names, to tell the script that only those apps should be built, instead
        # of just building all apps in the consumer_apps_resources repo
        subset_of_apps_to_build = sys.argv[2].split(",")
    else:
        subset_of_apps_to_build = None

    checkout_or_update_static_resources_repo()
    build_apks_from_resources(build_type, subset_of_apps_to_build)


if __name__ == "__main__":
    main()

