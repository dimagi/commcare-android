
import os
import subprocess 
import xml.etree.ElementTree as ET

# Script to build the .apks for all consumer apps, off of the latest release build of CommCare on jenkins
# All relative paths should be written assuming this script will be run from the PARENT directory of commcare-odk/ 


# Relative path to the directory where all user-provided information and files for each consumer app lives. 
# This directory should store a list of directories, 1 for each consumer app we are building.
# The expected format for a single app directory is as follows:
# - config.txt: a text file containing the following 5 pieces of information in order, on a single comma-separated line, e.g: app_id,app_domain,build_number,username,password
# - ic_launcher.zip: a zip file generated from Android Asset Studio of the desired app icon (MUST have this exact name)
PATH_TO_STATIC_RESOURCES_DIR = "./consumer-apps-resources"

# Relative path to the standalone directory
PATH_TO_STANDALONE_DIR = "./commcare-odk/app/standalone/"

# Relative path to the directory where all app assets should be placed
PATH_TO_ASSETS_DIR = PATH_TO_STANDALONE_DIR + "assets"


def checkout_or_update_static_resources_repo():
	if not os.path.exists(PATH_TO_STATIC_RESOURCES_DIR):
		subprocess.call(["git", "clone", "https://github.com/dimagi/consumer-apps-resources"])
	os.chdir(PATH_TO_STATIC_RESOURCES_DIR)
	subprocess.call(["git", "pull"])
	os.chdir('../')


def build_apks_from_resources():
	for (app_dir_name, sub_dir_list, files_list) in os.walk(PATH_TO_STATIC_RESOURCES_DIR):
		if app_dir_name != PATH_TO_STATIC_RESOURCES_DIR:
			build_apk_from_directory_contents(app_dir_name, files_list)


def build_apk_from_directory_contents(app_sub_dir, files_list):
	full_path_to_config_file = os.path.join(app_sub_dir, files_list[0])
	full_path_to_zipfile = os.path.join(app_sub_dir, files_list[1])

	unzip_app_icon(full_path_to_zipfile)
	app_id, domain, build_number, username, password = get_app_fields(full_path_to_config_file)
	password = '123' #TEMPORARY, REMOVE AFTER TESTING
	download_ccz(app_id, domain, build_number)
	download_restore_file(domain, username, password)
	assemble_apk(domain, build_number, username, password)
	move_apk(app_id)


def unzip_app_icon(zipfile_name):
	# -o option overwrites the existing files without prompting for confirmation
	subprocess.call(["unzip", "-o", zipfile_name, "-d", PATH_TO_STANDALONE_DIR])


def get_app_fields(config_filename):
	f = open(config_filename, 'r')
	line = f.readline().strip('\n')
	return tuple(line.split(","))


def download_ccz(app_id, domain, build_number):
	#TODO: Get HQ to implement downloading a specific build
	subprocess.call(["./scripts/download_app_into_standalone_asset.sh", domain, app_id, PATH_TO_ASSETS_DIR]) 


def download_restore_file(domain, username, password):
	subprocess.call(["./scripts/download_restore_into_standalone_asset.sh", domain, username, password, PATH_TO_ASSETS_DIR])


def assemble_apk(domain, build_number, username, password):
	subprocess.call(["gradle", "assembleStandaloneDebug", 
		"-Pcc_domain={}".format(domain), 
		"-Papplication_name={}".format(get_app_name_from_profile()), 
		"-Pis_consumer_app=true", 
		"-PversionCode={}".format(build_number),
		"-Pusername={}".format(username),
		"-Ppassword={}".format(password)])


def get_app_name_from_profile():
	tree = ET.parse(PATH_TO_ASSETS_DIR + '/direct_install/profile.ccpr')
	return tree.getroot().get("name")


def move_apk(app_id):
	subprocess.call(["mkdir", "-p", "./build/outputs/consumer_apks"]) 
	subprocess.call(["mv", "./build/outputs/apk/commcare-odk-standalone-debug.apk", "./build/outputs/consumer_apks/{}.apk".format(app_id)])


checkout_or_update_static_resources_repo()
build_apks_from_resources()



